package com.zl.teachertalk.ui.forum

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.zl.teachertalk.R
import com.zl.teachertalk.appContainer
import com.zl.teachertalk.base.BaseActivity
import com.zl.teachertalk.bean.ForumDetailLoadError
import com.zl.teachertalk.bean.ForumPostDetail
import com.zl.teachertalk.bean.ForumPostDetailUiState
import com.zl.teachertalk.databinding.ActivityForumPostDetailBinding
import com.zl.teachertalk.model.forum.ForumPostDetailViewModel
import com.zl.teachertalk.net.NetworkConfig
import com.zl.teachertalk.ui.auth.LoginActivity
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 帖子详情页：加载并展示帖子正文、作者、图片和互动统计。
 *
 * 点赞、收藏、评论及作者管理接口尚未达到接入条件，本轮只展示服务端已返回的状态。
 */
class ForumPostDetailActivity : BaseActivity<ActivityForumPostDetailBinding>() {

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

    override fun onCreateBinding(inflater: LayoutInflater): ActivityForumPostDetailBinding {
        return ActivityForumPostDetailBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetryDetail.setOnClickListener { viewModel.retry() }
        binding.btnShare.setOnClickListener { sharePost() }
        binding.btnMore.setOnClickListener { showPendingFeature() }
        binding.btnLike.setOnClickListener { showPendingFeature() }
        binding.btnComments.setOnClickListener {
            binding.scrollPostDetail.smoothScrollTo(0, binding.cardComments.top)
        }
        binding.btnFavorite.setOnClickListener { showPendingFeature() }
        binding.etComment.isFocusable = false
        binding.etComment.isCursorVisible = false
        binding.etComment.setOnClickListener { showPendingFeature() }
        binding.btnSendComment.setOnClickListener { showPendingFeature() }
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
        viewModel.loadPost()
    }

    private fun render(state: ForumPostDetailUiState) {
        binding.progressDetail.isVisible = state.isLoading
        binding.layoutDetailError.isVisible = state.loadError != null
        binding.scrollPostDetail.isVisible = state.post != null
        binding.btnShare.isEnabled = state.post != null

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

        state.post?.let { post ->
            bindPost(post)
        }
    }

    private fun bindPost(post: ForumPostDetail) {
        binding.tvAuthorName.text = post.authorNickname
        binding.tvAuthorMeta.text = formatRelativeTime(post.createdAt)
        bindAvatar(post.authorAvatarUrl)

        val title = post.title.trim()
        binding.tvPostTitle.isVisible = title.isNotEmpty()
        binding.tvPostTitle.text = title
        binding.tvPostContent.text = post.content
        binding.tvPostTime.text = formatRelativeTime(post.createdAt)

        bindImages(post.imageUrls)
        bindInteractionState(post)

        binding.tvCommentCount.text = post.commentCount.toString()
        binding.tvCommentsPending.isVisible = true
        binding.rvComments.isVisible = false
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

        val favoriteColor = if (post.favorited) activeColor else inactiveColor
        binding.ivFavoriteIcon.imageTintList = ColorStateList.valueOf(favoriteColor)
        binding.tvFavoriteCount.setTextColor(favoriteColor)
        binding.tvFavoriteCount.text = post.favoriteCount.toString()
        binding.tvCommentActionCount.text = post.commentCount.toString()
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

    private fun showPendingFeature() {
        Toast.makeText(
            this,
            R.string.forum_detail_feature_pending,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun formatRelativeTime(value: String): String {
        val timestamp = runCatching {
            API_TIME_FORMATTER.parse(value)?.time
        }.getOrNull() ?: return value
        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
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

        private val API_TIME_FORMATTER = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.ROOT
        ).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            isLenient = false
        }

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
