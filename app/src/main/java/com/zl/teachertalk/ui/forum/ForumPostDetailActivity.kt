package com.zl.teachertalk.ui.forum

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zl.teachertalk.R
import com.zl.teachertalk.adapter.CommentAdapter
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.bean.ForumDetailLoadError
import com.zl.teachertalk.bean.ForumCommentError
import com.zl.teachertalk.bean.ForumInteractionError
import com.zl.teachertalk.bean.ForumPostDetail
import com.zl.teachertalk.bean.ForumPostDetailUiState
import com.zl.teachertalk.databinding.ActivityForumPostDetailBinding
import com.zl.teachertalk.databinding.DialogPostManagementBinding
import com.zl.teachertalk.model.forum.ForumPostDetailViewModel
import com.zl.teachertalk.net.NetworkConfig
import com.zl.teachertalk.ui.auth.LoginActivity
import com.zl.teachertalk.utils.ForumTimeFormatter
import com.google.android.material.imageview.ShapeableImageView

/**
 * 帖子详情页：加载并展示帖子正文、作者、图片和互动统计。
 *
 * 点赞、收藏、评论和作者编辑已接入；关注、转发、删除及置顶尚未开放。
 */
class ForumPostDetailActivity : BaseActivity<ActivityForumPostDetailBinding>() {

    private val commentAdapter = CommentAdapter()

    private var lastBoundPost: ForumPostDetail? = null

    private val commentPrefetchDistance: Int by lazy {
        resources.getDimensionPixelSize(R.dimen.forum_detail_comment_prefetch_distance)
    }

    private val postId: Long by lazy {
        intent.getLongExtra(EXTRA_POST_ID, INVALID_POST_ID)
    }

    private val viewModel: ForumPostDetailViewModel by lazy {
        ViewModelProvider(
            this,
            ForumPostDetailViewModel.factory(
                postId = postId,
                forumRepository = appContainer.forumRepository,
                sessionManager = appContainer.sessionManager
            )
        )[ForumPostDetailViewModel::class.java]
    }

    private val editPostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(RESULT_OK)
            viewModel.reloadPost()
        }
    }

    override fun onCreateBinding(inflater: LayoutInflater): ActivityForumPostDetailBinding {
        return ActivityForumPostDetailBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyPageImeInsets()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetryDetail.setOnClickListener { viewModel.retry() }
        binding.btnRetryComments.setOnClickListener { viewModel.retryComments() }
        binding.btnShare.setOnClickListener { sharePost() }
        binding.btnMore.setOnClickListener { showPostManagementSheet() }
        binding.btnMore.visibility = View.INVISIBLE
        binding.btnLike.setOnClickListener { viewModel.toggleLike() }
        binding.btnComments.setOnClickListener {
            binding.scrollPostDetail.smoothScrollTo(0, binding.cardComments.top)
        }
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.rvComments.adapter = commentAdapter
        ContextCompat.getDrawable(this, R.drawable.divider_forum_detail)?.let { divider ->
            binding.rvComments.addItemDecoration(
                DividerItemDecoration(
                    this,
                    DividerItemDecoration.VERTICAL
                ).apply {
                    setDrawable(divider)
                }
            )
        }
        binding.etComment.doAfterTextChanged {
            updateCommentSubmitState()
        }
        binding.etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitComment()
                true
            } else {
                false
            }
        }
        binding.btnSendComment.setOnClickListener { submitComment() }
        binding.scrollPostDetail.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val content = binding.scrollPostDetail.getChildAt(0)
            if (content != null &&
                scrollY + binding.scrollPostDetail.height >=
                content.measuredHeight - commentPrefetchDistance
            ) {
                viewModel.loadMoreComments()
            }
        }
        binding.btnFollow.isVisible = false
        binding.btnRepost.isVisible = false

        viewModel.uiState.observe(this) { state ->
            render(state)
        }
        viewModel.loginRequiredEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                openLoginAndClearTask()
            }
        }
        viewModel.interactionErrorEvents.observe(this) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            showInteractionError(error)
        }
        viewModel.interactionSucceededEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                setResult(RESULT_OK)
            }
        }
        viewModel.commentErrorEvents.observe(this) { event ->
            val error = event.getContentIfNotHandled() ?: return@observe
            showCommentError(error)
        }
        viewModel.commentCreatedEvents.observe(this) { event ->
            if (event.getContentIfNotHandled() != null) {
                setResult(RESULT_OK)
                binding.etComment.text?.clear()
                binding.etComment.clearFocus()
                hideKeyboard()
                binding.cardComments.post {
                    binding.scrollPostDetail.smoothScrollTo(
                        0,
                        binding.cardComments.bottom
                    )
                }
            }
        }
        viewModel.loadPost()
    }

    private fun render(state: ForumPostDetailUiState) {
        binding.progressDetail.isVisible = state.isLoading
        binding.layoutDetailError.isVisible = state.loadError != null
        binding.scrollPostDetail.isVisible = state.post != null
        binding.btnShare.isEnabled = state.post != null
        binding.btnMore.visibility =
            if (state.post?.let(::isCurrentUserAuthor) == true) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        val interactionsEnabled = state.post != null &&
            !state.isLikeSubmitting &&
            !state.isFavoriteSubmitting &&
            !state.isSubmittingComment
        binding.btnLike.isEnabled = interactionsEnabled
        binding.btnFavorite.isEnabled = interactionsEnabled

        state.loadError?.let { error ->
            binding.tvDetailStateMessage.setText(
                if (error == ForumDetailLoadError.NOT_FOUND) {
                    R.string.forum_detail_not_found
                } else {
                    R.string.forum_detail_load_failed
                }
            )
            binding.btnRetryDetail.isVisible = error != ForumDetailLoadError.NOT_FOUND
        }

        val post = state.post
        if (post == null) {
            lastBoundPost = null
        } else if (post != lastBoundPost) {
            bindPost(post)
            lastBoundPost = post
        }
        renderComments(state)
        updateCommentSubmitState(state)
    }

    private fun bindPost(post: ForumPostDetail) {
        binding.tvAuthorName.text = post.authorNickname
        binding.tvAuthorMeta.text = ForumTimeFormatter.format(post.createdAt)
        bindAvatar(post.authorAvatarUrl)

        val title = post.title.trim()
        binding.tvPostTitle.isVisible = title.isNotEmpty()
        binding.tvPostTitle.text = title
        binding.tvPostContent.text = post.content
        binding.tvPostTime.text = ForumTimeFormatter.format(post.createdAt)

        bindImages(post.imageUrls)
        bindInteractionState(post)

        binding.tvCommentCount.text = post.commentCount.toString()
    }

    private fun renderComments(state: ForumPostDetailUiState) {
        commentAdapter.submitList(state.comments)
        binding.progressComments.isVisible = state.isLoadingComments
        binding.progressCommentsLoadMore.isVisible = state.isLoadingMoreComments
        binding.rvComments.isVisible = state.comments.isNotEmpty()

        val showState = !state.isLoadingComments &&
            state.comments.isEmpty() &&
            (state.commentsLoadFailed || state.commentsPage > 0)
        binding.layoutCommentsState.isVisible = showState
        if (showState) {
            binding.tvCommentsState.setText(
                if (state.commentsLoadFailed) {
                    R.string.forum_comments_load_failed
                } else {
                    R.string.forum_comments_empty
                }
            )
            binding.btnRetryComments.isVisible = state.commentsLoadFailed
        } else {
            binding.btnRetryComments.isVisible = false
        }
    }

    private fun bindAvatar(avatarUrl: String?) {
        val normalizedUrl = avatarUrl?.trim().orEmpty()
        if (normalizedUrl.isEmpty()) {
            Glide.with(binding.ivAuthorAvatar).clear(binding.ivAuthorAvatar)
            binding.ivAuthorAvatar.setImageResource(R.drawable.icon_user_avatar)
            return
        }

        Glide.with(binding.ivAuthorAvatar)
            .load(NetworkConfig.resolveMediaUrl(normalizedUrl))
            .placeholder(R.drawable.icon_user_avatar)
            .error(R.drawable.icon_user_avatar)
            .centerCrop()
            .into(binding.ivAuthorAvatar)
    }

    private fun bindImages(imageUrls: List<String>) {
        val imageViews = listOf(
            binding.ivPostDetailImage1,
            binding.ivPostDetailImage2,
            binding.ivPostDetailImage3,
            binding.ivPostDetailImage4,
            binding.ivPostDetailImage5
        )
        val normalizedUrls = imageUrls
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_IMAGE_COUNT)
            .toList()

        imageViews.forEachIndexed { index, imageView ->
            bindImage(imageView, normalizedUrls.getOrNull(index), index)
        }
        val hasImages = normalizedUrls.isNotEmpty()
        binding.layoutPostMedia.isVisible = hasImages
        binding.layoutPostImage.isVisible = false
        binding.scrollPostImages.isVisible = hasImages
    }

    private fun bindImage(
        imageView: ShapeableImageView,
        imageUrl: String?,
        position: Int
    ) {
        if (imageUrl == null) {
            imageView.isVisible = false
            Glide.with(this).clear(imageView)
            imageView.setImageDrawable(null)
            return
        }

        imageView.isVisible = true
        imageView.contentDescription = getString(
            R.string.forum_detail_image_position,
            position + 1
        )
        Glide.with(this)
            .load(NetworkConfig.resolveMediaUrl(imageUrl))
            .centerCrop()
            .into(imageView)
    }

    private fun bindInteractionState(post: ForumPostDetail) {
        val activeColor = getColor(R.color.forum_detail_accent)
        val inactiveColor = getColor(R.color.forum_detail_text_secondary)

        binding.ivLikeIcon.setImageResource(
            if (post.liked) {
                R.drawable.ic_like_filled
            } else {
                R.drawable.ic_like_outline
            }
        )
        val likeColor = if (post.liked) activeColor else inactiveColor
        binding.ivLikeIcon.imageTintList = ColorStateList.valueOf(likeColor)
        binding.tvLikeCount.setTextColor(likeColor)
        binding.tvLikeCount.text = post.likeCount.toString()
        binding.btnLike.contentDescription = getString(
            if (post.liked) R.string.forum_detail_unlike else R.string.forum_detail_like
        )

        val favoriteColor = if (post.favorited) activeColor else inactiveColor
        binding.ivFavoriteIcon.imageTintList = ColorStateList.valueOf(favoriteColor)
        binding.tvFavoriteCount.setTextColor(favoriteColor)
        binding.tvFavoriteCount.text = post.favoriteCount.toString()
        binding.btnFavorite.contentDescription = getString(
            if (post.favorited) {
                R.string.forum_detail_unfavorite
            } else {
                R.string.forum_detail_favorite
            }
        )
        binding.tvCommentActionCount.text = post.commentCount.toString()
    }

    private fun isCurrentUserAuthor(post: ForumPostDetail): Boolean {
        return post.authorId == appContainer.sessionManager.userInfo?.id
    }

    private fun showPostManagementSheet() {
        val post = viewModel.uiState.value?.post ?: return
        if (!isCurrentUserAuthor(post)) return

        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogPostManagementBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvEditPost.setOnClickListener {
            dialog.dismiss()
            editPostLauncher.launch(
                CreatePostActivity.createEditIntent(
                    context = this,
                    postId = post.id
                )
            )
        }
        sheetBinding.tvCancelPostManagement.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateCommentSubmitState(
        state: ForumPostDetailUiState? = viewModel.uiState.value
    ) {
        val canComment = state?.post != null &&
            state.commentsPage > 0 &&
            !state.isSubmittingComment &&
            !state.isLoadingMoreComments &&
            !state.isLikeSubmitting &&
            !state.isFavoriteSubmitting
        val hasContent = binding.etComment.text
            ?.toString()
            ?.trim()
            ?.isNotEmpty() == true
        val canSubmit = canComment && hasContent

        binding.etComment.isEnabled = canComment
        binding.btnSendComment.isEnabled = canSubmit
        binding.btnSendComment.alpha = if (canSubmit) 1f else DISABLED_ALPHA
    }

    private fun submitComment() {
        if (!binding.btnSendComment.isEnabled) return
        viewModel.submitComment(binding.etComment.text?.toString().orEmpty())
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.etComment.windowToken, 0)
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

    private fun sharePost() {
        val post = viewModel.uiState.value?.post ?: return
        val shareText = listOf(post.title.trim(), post.content.trim())
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n\n")
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(
            Intent.createChooser(shareIntent, getString(R.string.forum_detail_share))
        )
    }

    private fun showInteractionError(error: ForumInteractionError) {
        val messageRes = when (error) {
            ForumInteractionError.LIKE_FAILED -> R.string.forum_detail_like_failed
            ForumInteractionError.FAVORITE_FAILED -> R.string.forum_detail_favorite_failed
            ForumInteractionError.POST_NOT_FOUND -> R.string.forum_detail_not_found
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun showCommentError(error: ForumCommentError) {
        val messageRes = when (error) {
            ForumCommentError.EMPTY_CONTENT -> R.string.forum_comment_empty_content
            ForumCommentError.TOO_LONG -> R.string.forum_comment_too_long
            ForumCommentError.SUBMIT_FAILED -> R.string.forum_comment_submit_failed
            ForumCommentError.LOAD_MORE_FAILED -> R.string.forum_comments_load_more_failed
            ForumCommentError.POST_NOT_FOUND -> R.string.forum_detail_not_found
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
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
        private const val EXTRA_POST_ID = "post_id"
        private const val INVALID_POST_ID = 0L
        private const val MAX_IMAGE_COUNT = 5
        private const val DISABLED_ALPHA = 0.5f

        /**
         * 创建打开帖子详情的 Intent。
         *
         * @param context 用于创建 Intent 的上下文
         * @param postId 帖子编号
         * @return 可直接交给 ActivityResultLauncher 或 startActivity 的 Intent
         */
        fun createIntent(context: Context, postId: Long): Intent {
            return Intent(context, ForumPostDetailActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, postId)
            }
        }
    }
}
