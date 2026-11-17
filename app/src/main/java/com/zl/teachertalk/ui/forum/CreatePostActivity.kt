package com.zl.teachertalk.ui.forum

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.imageview.ShapeableImageView
import com.zl.teachertalk.R
import com.zl.teachertalk.adapter.PostImagePreviewAdapter
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.bean.CreatePostError
import com.zl.teachertalk.bean.CreatePostUiState
import com.zl.teachertalk.bean.ForumPostDetail
import com.zl.teachertalk.bean.PostEditorImage
import com.zl.teachertalk.bean.PostEditorLoadError
import com.zl.teachertalk.databinding.ActivityCreatePostBinding
import com.zl.teachertalk.databinding.DialogImagePreviewBinding
import com.zl.teachertalk.databinding.DialogPostImageActionsBinding
import com.zl.teachertalk.model.forum.CreatePostViewModel
import com.zl.teachertalk.net.NetworkConfig
import com.zl.teachertalk.ui.auth.LoginActivity
import com.zl.teachertalk.utils.PostImageCompressor

/**
 * 创建和编辑帖子共用页面：维护标题、正文和有序图片草稿。
 */
class CreatePostActivity : BaseActivity<ActivityCreatePostBinding>() {

    private val editorImages = mutableListOf<PostEditorImage>()
    private var isSubmitting = false
    private var isLoadingOriginal = false
    private var isLoadFailed = false
    private var hasAppliedOriginal = false

    private val postId: Long? by lazy {
        intent.getLongExtra(EXTRA_POST_ID, INVALID_POST_ID)
            .takeIf { it > INVALID_POST_ID }
    }

    private val isEditMode: Boolean
        get() = postId != null

    private val viewModel: CreatePostViewModel by lazy {
        ViewModelProvider(
            this,
            CreatePostViewModel.factory(
                forumRepository = appContainer.forumRepository,
                imageCompressor = appContainer.postImageCompressor,
                sessionManager = appContainer.sessionManager,
                postId = postId
            )
        )[CreatePostViewModel::class.java]
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_COUNT)
    ) { uris ->
        addSelectedImages(uris)
    }

    override fun onCreateBinding(inflater: LayoutInflater): ActivityCreatePostBinding {
        return ActivityCreatePostBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyPageImeInsets()
        restoreEditorState(savedInstanceState)
        isLoadingOriginal = isEditMode
        binding.tvPageTitle.setText(
            if (isEditMode) R.string.post_edit_page_title else R.string.post_page_title
        )
        binding.scrollEditor.isVisible = !isEditMode
        binding.progressEditorLoading.isVisible = isEditMode

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPublish.setOnClickListener {
            viewModel.savePost(
                title = binding.etTitle.text?.toString().orEmpty(),
                content = binding.etContent.text?.toString().orEmpty(),
                images = editorImages.toList()
            )
        }
        binding.btnRetryEditor.setOnClickListener {
            viewModel.retryLoadOriginalPost()
        }
        binding.btnAddImage.setOnClickListener {
            openImagePicker()
        }
        binding.etContent.doAfterTextChanged { editable ->
            updatePublishButtonState(editable?.isNotBlank() == true)
        }

        updatePublishButtonState()
        renderImageSlots()
        observeViewModel()
        if (isEditMode) {
            viewModel.loadOriginalPost()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            KEY_EDITOR_IMAGES,
            ArrayList(
                editorImages.map { image ->
                    when (image) {
                        is PostEditorImage.Existing -> "$IMAGE_TYPE_EXISTING${image.url}"
                        is PostEditorImage.Local -> "$IMAGE_TYPE_LOCAL${image.uriString}"
                    }
                }
            )
        )
        outState.putBoolean(KEY_HAS_APPLIED_ORIGINAL, hasAppliedOriginal)
        super.onSaveInstanceState(outState)
    }

    private fun restoreEditorState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        hasAppliedOriginal = savedInstanceState.getBoolean(
            KEY_HAS_APPLIED_ORIGINAL,
            false
        )
        val savedImages = savedInstanceState.getStringArrayList(KEY_EDITOR_IMAGES)
            ?: return
        savedImages
            .take(MAX_IMAGE_COUNT)
            .forEach { value ->
                parseEditorImage(value)?.let(editorImages::add)
            }
    }

    private fun parseEditorImage(value: String): PostEditorImage? {
        return when {
            value.startsWith(IMAGE_TYPE_EXISTING) -> {
                PostEditorImage.Existing(value.removePrefix(IMAGE_TYPE_EXISTING))
            }

            value.startsWith(IMAGE_TYPE_LOCAL) -> {
                PostEditorImage.Local(value.removePrefix(IMAGE_TYPE_LOCAL))
            }

            else -> null
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            renderEditorState(state)
        }
        viewModel.errorEvents.observe(this) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            showCreatePostError(error)
        }
        viewModel.postSavedEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(
                    this,
                    if (isEditMode) {
                        R.string.post_save_succeeded
                    } else {
                        R.string.post_publish_succeeded
                    },
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            }
        }
        viewModel.loginRequiredEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                openLoginAndClearTask()
            }
        }
    }

    private fun renderEditorState(state: CreatePostUiState) {
        isSubmitting = state.isSubmitting
        isLoadingOriginal = state.isLoadingOriginal
        isLoadFailed = state.loadError != null

        if (isEditMode && !hasAppliedOriginal && state.originalPost != null) {
            applyOriginalPost(state.originalPost)
        }

        binding.progressEditorLoading.isVisible = state.isLoadingOriginal
        binding.layoutEditorError.isVisible = state.loadError != null
        binding.scrollEditor.isVisible = !state.isLoadingOriginal && state.loadError == null

        state.loadError?.let { error ->
            binding.tvEditorErrorMessage.setText(errorMessageRes(error))
            binding.btnRetryEditor.isVisible = error != PostEditorLoadError.NOT_FOUND &&
                error != PostEditorLoadError.NO_PERMISSION
        }

        renderImageSlots()
        updatePublishButtonState()
    }

    private fun applyOriginalPost(post: ForumPostDetail) {
        binding.etTitle.setText(post.title)
        binding.etContent.setText(post.content)
        binding.etContent.setSelection(binding.etContent.text.length)

        editorImages.clear()
        editorImages.addAll(
            post.imageUrls
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_IMAGE_COUNT)
                .map { url -> PostEditorImage.Existing(url) }
                .toList()
        )
        hasAppliedOriginal = true
    }

    private fun errorMessageRes(error: PostEditorLoadError): Int {
        return when (error) {
            PostEditorLoadError.NOT_FOUND -> R.string.forum_detail_not_found
            PostEditorLoadError.NO_PERMISSION -> R.string.post_no_permission
            PostEditorLoadError.NETWORK,
            PostEditorLoadError.SERVER,
            PostEditorLoadError.UNKNOWN -> R.string.post_edit_load_failed
        }
    }

    private fun applyPageImeInsets() {
        val contentView = window.decorView.findViewById<View>(Window.ID_ANDROID_CONTENT)
            ?: return
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val safeAreaInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(
                safeAreaInsets.left,
                safeAreaInsets.top,
                safeAreaInsets.right,
                maxOf(safeAreaInsets.bottom, imeBottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }

    private fun openImagePicker() {
        if (isSubmitting || isLoadingOriginal || isLoadFailed) return
        if (editorImages.size >= MAX_IMAGE_COUNT) {
            showImageLimitMessage()
            return
        }
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty() || isSubmitting || isLoadingOriginal || isLoadFailed) return

        val remainingCount = MAX_IMAGE_COUNT - editorImages.size
        val acceptedUris = uris.take(remainingCount)
        editorImages.addAll(
            acceptedUris.map { uri ->
                PostEditorImage.Local(uri.toString())
            }
        )
        if (acceptedUris.size < uris.size) {
            showImageLimitMessage()
        }
        renderImageSlots()
    }

    private fun renderImageSlots() {
        val imageSlots = listOf(
            binding.ivPostImage1,
            binding.ivPostImage2,
            binding.ivPostImage3,
            binding.ivPostImage4,
            binding.ivPostImage5
        )

        imageSlots.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                showImageActions(index)
            }
            renderImageSlot(
                imageView = imageView,
                image = editorImages.getOrNull(index),
                position = index
            )
        }
        binding.ivPostImage4.isVisible = editorImages.size >= 4
        binding.ivPostImage5.isVisible = editorImages.size >= 5

        val canAddMore = !isSubmitting &&
            !isLoadingOriginal &&
            !isLoadFailed &&
            editorImages.size < MAX_IMAGE_COUNT
        binding.btnAddImage.isEnabled = canAddMore
        binding.btnAddImage.alpha = if (canAddMore) 1f else DISABLED_ALPHA
    }

    private fun renderImageSlot(
        imageView: ShapeableImageView,
        image: PostEditorImage?,
        position: Int
    ) {
        val hasImage = image != null
        val canManageImage = hasImage &&
            !isSubmitting &&
            !isLoadingOriginal &&
            !isLoadFailed
        imageView.isClickable = canManageImage
        imageView.isFocusable = canManageImage
        imageView.importantForAccessibility = if (hasImage) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        imageView.contentDescription = if (hasImage) {
            getString(R.string.post_image_slot_actions, position + 1)
        } else {
            null
        }

        if (image == null) {
            Glide.with(this).clear(imageView)
            imageView.setImageDrawable(null)
            return
        }

        val model = when (image) {
            is PostEditorImage.Existing ->
                NetworkConfig.resolveMediaUrl(image.url)

            is PostEditorImage.Local -> Uri.parse(image.uriString)
        }
        Glide.with(this)
            .load(model)
            .placeholder(R.drawable.bg_post_img_content)
            .error(R.drawable.bg_post_img_content)
            .centerCrop()
            .into(imageView)
    }

    private fun showImageActions(position: Int) {
        if (isSubmitting ||
            isLoadingOriginal ||
            isLoadFailed ||
            position !in editorImages.indices
        ) {
            return
        }
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogPostImageActionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvPreviewImage.setOnClickListener {
            dialog.setOnDismissListener {
                showImagePreview(position)
            }
            dialog.dismiss()
        }
        sheetBinding.tvDeleteImage.setOnClickListener {
            dialog.dismiss()
            deleteImage(position)
        }
        sheetBinding.tvCancelImageAction.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showImagePreview(startPosition: Int) {
        val previewImages = editorImages.map(::previewUri)
        if (startPosition !in previewImages.indices) return

        val previewBinding = DialogImagePreviewBinding.inflate(layoutInflater)
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val previewAdapter = PostImagePreviewAdapter(previewImages) {
            dialog.dismiss()
        }
        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePreviewPosition(
                    previewBinding = previewBinding,
                    position = position,
                    totalCount = previewImages.size
                )
            }
        }

        dialog.setContentView(previewBinding.root)
        val dialogWindow = dialog.window ?: return
        WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        dialogWindow.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        WindowCompat.getInsetsController(
            dialogWindow,
            previewBinding.previewRoot
        ).isAppearanceLightNavigationBars = false
        applyPreviewPositionInsets(previewBinding)

        previewBinding.vpImagePreview.adapter = previewAdapter
        previewBinding.vpImagePreview.offscreenPageLimit = 1
        previewBinding.vpImagePreview.registerOnPageChangeCallback(pageChangeCallback)
        previewBinding.vpImagePreview.setCurrentItem(startPosition, false)
        updatePreviewPosition(
            previewBinding = previewBinding,
            position = startPosition,
            totalCount = previewImages.size
        )

        dialog.setOnDismissListener {
            previewBinding.vpImagePreview.unregisterOnPageChangeCallback(pageChangeCallback)
            previewBinding.vpImagePreview.adapter = null
        }

        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        ViewCompat.requestApplyInsets(previewBinding.previewRoot)
    }

    private fun previewUri(image: PostEditorImage): Uri {
        return when (image) {
            is PostEditorImage.Existing ->
                Uri.parse(NetworkConfig.resolveMediaUrl(image.url))

            is PostEditorImage.Local -> Uri.parse(image.uriString)
        }
    }

    private fun applyPreviewPositionInsets(previewBinding: DialogImagePreviewBinding) {
        val baseMargin = resources.getDimensionPixelSize(
            R.dimen.post_image_preview_position_margin
        )
        ViewCompat.setOnApplyWindowInsetsListener(
            previewBinding.tvPreviewPosition
        ) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            val endInset = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                safeInsets.left
            } else {
                safeInsets.right
            }
            val marginParams = view.layoutParams as ViewGroup.MarginLayoutParams
            marginParams.marginEnd = baseMargin + endInset
            marginParams.bottomMargin = baseMargin + safeInsets.bottom
            view.layoutParams = marginParams
            insets
        }
    }

    private fun updatePreviewPosition(
        previewBinding: DialogImagePreviewBinding,
        position: Int,
        totalCount: Int
    ) {
        previewBinding.tvPreviewPosition.text = getString(
            R.string.post_image_preview_position,
            position + 1,
            totalCount
        )
    }

    private fun deleteImage(position: Int) {
        if (position !in editorImages.indices) return
        editorImages.removeAt(position)
        renderImageSlots()
    }

    private fun showImageLimitMessage() {
        Toast.makeText(
            this,
            getString(R.string.post_image_limit_reached, MAX_IMAGE_COUNT),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updatePublishButtonState(
        hasContent: Boolean = binding.etContent.text?.isNotBlank() == true
    ) {
        val canSubmit = !isSubmitting && !isLoadingOriginal && !isLoadFailed
        binding.btnPublish.text = when {
            isSubmitting && isEditMode -> getText(R.string.post_saving)
            isSubmitting -> getText(R.string.post_publishing)
            isEditMode -> getText(R.string.post_save)
            else -> getText(R.string.post_publish)
        }
        binding.btnPublish.isEnabled = canSubmit
        binding.btnPublish.alpha = if (canSubmit) 1f else DISABLED_ALPHA
        binding.btnPublish.setBackgroundResource(
            if (hasContent) {
                R.drawable.bg_post_publish_button
            } else {
                R.drawable.bg_post_publish_button_light
            }
        )
        binding.btnPublish.setTextColor(
            getColor(
                if (hasContent) {
                    R.color.white
                } else {
                    R.color.main_text_primary
                }
            )
        )
    }

    private fun showCreatePostError(error: CreatePostError) {
        val message = when (error) {
            CreatePostError.CONTENT_REQUIRED -> getString(R.string.post_content_required)
            CreatePostError.TOO_MANY_IMAGES ->
                getString(R.string.post_image_limit_reached, MAX_IMAGE_COUNT)

            CreatePostError.IMAGE_DECODE_FAILED ->
                getString(R.string.post_image_decode_failed)

            CreatePostError.IMAGE_TOO_LARGE ->
                getString(
                    R.string.post_image_too_large,
                    PostImageCompressor.MAX_UPLOAD_SIZE_MB
                )

            CreatePostError.IMAGE_WRITE_FAILED ->
                getString(R.string.post_image_write_failed)

            CreatePostError.IMAGE_UPLOAD_FAILED ->
                getString(R.string.post_image_upload_failed)

            CreatePostError.INVALID_POST -> getString(R.string.post_invalid)
            CreatePostError.NO_PERMISSION -> getString(R.string.post_no_permission)
            CreatePostError.POST_NOT_FOUND -> getString(R.string.forum_detail_not_found)
            CreatePostError.PUBLISH_FAILED -> getString(R.string.post_publish_failed)
            CreatePostError.UPDATE_FAILED -> getString(R.string.post_save_failed)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openLoginAndClearTask() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        private const val MAX_IMAGE_COUNT = 5
        private const val INVALID_POST_ID = 0L
        private const val EXTRA_POST_ID = "post_id"
        private const val KEY_EDITOR_IMAGES = "editor_images"
        private const val KEY_HAS_APPLIED_ORIGINAL = "has_applied_original"
        private const val IMAGE_TYPE_EXISTING = "existing:"
        private const val IMAGE_TYPE_LOCAL = "local:"
        private const val DISABLED_ALPHA = 0.5f

        /**
         * 创建进入编辑帖子页面的 Intent。
         *
         * @param context 用于创建 Intent 的上下文
         * @param postId 要编辑的帖子编号
         * @return 编辑页面 Intent
         */
        fun createEditIntent(context: Context, postId: Long): Intent {
            return Intent(context, CreatePostActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
            }
        }
    }
}
