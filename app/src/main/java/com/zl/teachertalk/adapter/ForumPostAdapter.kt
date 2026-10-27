package com.zl.teachertalk.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zl.teachertalk.R
import com.zl.teachertalk.bean.ForumPost
import com.zl.teachertalk.databinding.ItemForumPostBinding
import com.zl.teachertalk.net.NetworkConfig

/**
 * 论坛帖子列表适配器。
 *
 * 卡片展示作者信息、标题或正文预览、缩略图以及评论和点赞统计。
 *
 * @param onPostClick 点击帖子卡片时回调帖子编号
 */
class ForumPostAdapter(
    private val onPostClick: (Long) -> Unit
) : ListAdapter<ForumPost, ForumPostAdapter.PostViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemForumPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding, onPostClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 单条帖子卡片的 ViewHolder。
     */
    class PostViewHolder(
        private val binding: ItemForumPostBinding,
        private val onPostClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * 将帖子数据绑定到列表卡片。
         *
         * @param post 待展示的帖子
         */
        fun bind(post: ForumPost) {
            binding.root.isClickable = true
            binding.root.isFocusable = true
            binding.root.setOnClickListener {
                onPostClick(post.id)
            }
            bindAuthor(post)
            bindText(post)
            bindThumbnails(post.thumbnailUrls.orEmpty())
            bindStats(post)
        }

        private fun bindAuthor(post: ForumPost) {
            val context = binding.root.context
            binding.tvPostAuthorName.text = post.authorNickname.trim().ifEmpty {
                context.getString(R.string.forum_post_unknown_author)
            }

            val avatarUrl = post.authorAvatarUrl?.trim().orEmpty()
            if (avatarUrl.isEmpty()) {
                Glide.with(binding.root).clear(binding.ivPostAuthorAvatar)
                binding.ivPostAuthorAvatar.setImageResource(R.drawable.icon_user_avatar)
                return
            }

            Glide.with(binding.root)
                .load(NetworkConfig.resolveMediaUrl(avatarUrl))
                .placeholder(R.drawable.icon_user_avatar)
                .error(R.drawable.icon_user_avatar)
                .centerCrop()
                .into(binding.ivPostAuthorAvatar)
        }

        private fun bindText(post: ForumPost) {
            val title = post.title.trim()
            if (title.isNotEmpty()) {
                binding.tvPostTitle.text = title
                binding.tvPostTitle.isVisible = true
                binding.tvPostPreview.isVisible = false
                binding.tvPostPreview.text = null
            } else {
                binding.tvPostTitle.isVisible = false
                binding.tvPostTitle.text = null
                binding.tvPostPreview.text = formatContentPreview(post.contentPreview)
                binding.tvPostPreview.isVisible = true
            }
        }

        private fun bindThumbnails(thumbnailUrls: List<String>) {
            val imageSlots = listOf(
                binding.ivPostThumbnail1,
                binding.ivPostThumbnail2,
                binding.ivPostThumbnail3
            )
            val imageUrls = thumbnailUrls
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_THUMBNAIL_COUNT)
                .toList()

            imageSlots.forEachIndexed { index, imageView ->
                val imageUrl = imageUrls.getOrNull(index)
                if (imageUrl == null) {
                    imageView.visibility = View.INVISIBLE
                    Glide.with(binding.root).clear(imageView)
                    imageView.setImageDrawable(null)
                } else {
                    imageView.visibility = View.VISIBLE
                    Glide.with(binding.root)
                        .load(NetworkConfig.resolveMediaUrl(imageUrl))
                        .centerCrop()
                        .into(imageView)
                }
            }
            binding.layoutThumbnails.isVisible = imageUrls.isNotEmpty()
        }

        private fun bindStats(post: ForumPost) {
            val context = binding.root.context
            binding.tvPostCommentCount.text = post.commentCount.toString()
            binding.tvPostLikeCount.text = post.likeCount.toString()
            binding.layoutPostComments.contentDescription = context.getString(
                R.string.forum_post_comment_count,
                post.commentCount
            )
            binding.layoutPostLikes.contentDescription = context.getString(
                R.string.forum_post_like_count,
                post.likeCount
            )

            val activeColor = context.getColor(R.color.forum_detail_accent)
            val inactiveColor = context.getColor(R.color.forum_detail_text_secondary)
            val likeColor = if (post.liked) activeColor else inactiveColor
            binding.ivPostLikeIcon.setImageResource(
                if (post.liked) {
                    R.drawable.ic_like_filled
                } else {
                    R.drawable.ic_like_outline
                }
            )
            binding.ivPostLikeIcon.imageTintList = ColorStateList.valueOf(likeColor)
            binding.tvPostLikeCount.setTextColor(likeColor)
        }

        private fun formatContentPreview(contentPreview: String): String {
            val normalized = contentPreview
                .trim()
                .replace(WHITESPACE_REGEX, " ")
            val codePoints = normalized.codePoints().toArray()
            if (codePoints.size <= MAX_PREVIEW_CODE_POINTS) {
                return normalized
            }
            val collapsed = StringBuilder()
            repeat(MAX_COLLAPSED_CODE_POINTS) { index ->
                collapsed.appendCodePoint(codePoints[index])
            }
            return collapsed.append(ELLIPSIS).toString()
        }

    }

    private companion object {
        const val MAX_THUMBNAIL_COUNT = 3
        const val MAX_PREVIEW_CODE_POINTS = 52
        const val MAX_COLLAPSED_CODE_POINTS = 49
        const val ELLIPSIS = "..."
        val WHITESPACE_REGEX = Regex("\\s+")

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ForumPost>() {
            override fun areItemsTheSame(oldItem: ForumPost, newItem: ForumPost): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ForumPost, newItem: ForumPost): Boolean {
                return oldItem == newItem
            }
        }
    }
}
