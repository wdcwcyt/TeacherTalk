package com.zl.teachertalk.ui.forum

import android.app.Dialog
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
import com.zl.teachertalk.databinding.ActivityCreatePostBinding
import com.zl.teachertalk.databinding.DialogImagePreviewBinding
import com.zl.teachertalk.databinding.DialogPostImageActionsBinding
import com.zl.teachertalk.model.forum.CreatePostViewModel
import com.zl.teachertalk.utils.PostImageCompressor

/**
 * 发布帖子页：提供标题、正文和图片选择入口。
 *
 * 负责选择图片、展示本地预览，并通过 ViewModel 完成压缩、上传和发帖。
 */
class CreatePostActivity : BaseActivity<ActivityCreatePostBinding>() {

    private val selectedImageUris = mutableListOf<Uri>()
    private var isSubmitting = false

    private val viewModel: CreatePostViewModel by lazy {
        ViewModelProvider(
            this,
            CreatePostViewModel.factory(
                forumRepository = appContainer.forumRepository,
                imageCompressor = appContainer.postImageCompressor
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

        setViewHeightToStatusBar(binding.statusBarSpacer)
        applyPageImeInsets()
        restoreSelectedImages(savedInstanceState)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPublish.setOnClickListener {
            viewModel.publishPost(
                title = binding.etTitle.text?.toString().orEmpty(),
                content = binding.etContent.text?.toString().orEmpty(),
                imageUris = selectedImageUris.toList()
            )
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
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            renderSubmitState(state)
        }
        viewModel.errorEvents.observe(this) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            showCreatePostError(error)
        }
        viewModel.postCreatedEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(this, R.string.post_publish_succeeded, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun applyPageImeInsets() {
        val contentView = window.decorView.findViewById<View>(Window.ID_ANDROID_CONTENT)
            ?: return
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val systemBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            ).bottom
            val imeBottom = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            ).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                maxOf(systemBarBottom, imeBottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            KEY_SELECTED_IMAGE_URIS,
            ArrayList(selectedImageUris.map { it.toString() })
        )
        super.onSaveInstanceState(outState)
    }

    private fun restoreSelectedImages(savedInstanceState: Bundle?) {
        val savedUris = savedInstanceState?.getStringArrayList(KEY_SELECTED_IMAGE_URIS)
            ?: return
        selectedImageUris.addAll(
            savedUris.take(MAX_IMAGE_COUNT).map { uri -> Uri.parse(uri) }
        )
    }

    private fun openImagePicker() {
        if (isSubmitting) return
        if (selectedImageUris.size >= MAX_IMAGE_COUNT) {
            showImageLimitMessage()
            return
        }
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty() || isSubmitting) return

        val remainingCount = MAX_IMAGE_COUNT - selectedImageUris.size
        val acceptedUris = uris.take(remainingCount)
        selectedImageUris.addAll(acceptedUris)
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
                uri = selectedImageUris.getOrNull(index),
                position = index
            )
        }
        binding.ivPostImage4.isVisible = selectedImageUris.size >= 4
        binding.ivPostImage5.isVisible = selectedImageUris.size >= 5

        val canAddMore = !isSubmitting && selectedImageUris.size < MAX_IMAGE_COUNT
        binding.btnAddImage.isEnabled = canAddMore
        binding.btnAddImage.alpha = if (canAddMore) 1f else DISABLED_ALPHA
    }

    private fun renderImageSlot(
        imageView: ShapeableImageView,
        uri: Uri?,
        position: Int
    ) {
        val hasImage = uri != null
        val canManageImage = hasImage && !isSubmitting
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

        if (uri == null) {
            Glide.with(this).clear(imageView)
            imageView.setImageDrawable(null)
            return
        }

        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(imageView)
    }

    private fun showImageActions(position: Int) {
        if (isSubmitting || position !in selectedImageUris.indices) return
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
        val previewImages = selectedImageUris.toList()
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
        if (position !in selectedImageUris.indices) return
        selectedImageUris.removeAt(position)
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
        binding.btnPublish.text = if (isSubmitting) {
            getText(R.string.post_publishing)
        } else {
            getText(R.string.post_publish)
        }
        binding.btnPublish.isEnabled = !isSubmitting
        binding.btnPublish.alpha = if (isSubmitting) DISABLED_ALPHA else 1f
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

    private fun renderSubmitState(state: CreatePostUiState) {
        isSubmitting = state.isSubmitting
        listOf(
            binding.ivPostImage1,
            binding.ivPostImage2,
            binding.ivPostImage3,
            binding.ivPostImage4,
            binding.ivPostImage5
        ).forEachIndexed { index, imageView ->
            val canManageImage = !isSubmitting && selectedImageUris.getOrNull(index) != null
            imageView.isClickable = canManageImage
            imageView.isFocusable = canManageImage
        }
        val canAddMore = !isSubmitting && selectedImageUris.size < MAX_IMAGE_COUNT
        binding.btnAddImage.isEnabled = canAddMore
        binding.btnAddImage.alpha = if (canAddMore) 1f else DISABLED_ALPHA
        updatePublishButtonState()
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
            CreatePostError.SESSION_EXPIRED -> getString(R.string.auth_error_session_expired)
            CreatePostError.PUBLISH_FAILED -> getString(R.string.post_publish_failed)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MAX_IMAGE_COUNT = 5
        const val KEY_SELECTED_IMAGE_URIS = "selected_image_uris"
        const val DISABLED_ALPHA = 0.5f
    }
}
