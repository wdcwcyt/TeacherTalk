package com.zl.teachertalk.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zl.teachertalk.bean.ForumPost
import com.zl.teachertalk.databinding.ItemForumPostBinding
import com.zl.teachertalk.net.NetworkConfig

/**
 * 论坛帖子列表适配器。
 *
 * 卡片优先展示标题；没有标题时展示两行正文预览，并在文字下方展示最多三张缩略图。
 */
class ForumPostAdapter : ListAdapter<ForumPost, ForumPostAdapter.PostViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemForumPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 单条帖子卡片的 ViewHolder。
     */
    class PostViewHolder(
        private val binding: ItemForumPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * 将帖子数据绑定到列表卡片。
         *
         * @param post 待展示的帖子
         */
        fun bind(post: ForumPost) {
            bindText(post)
            bindThumbnails(post.thumbnailUrls.orEmpty())
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
                    imageView.isVisible = false
                    Glide.with(binding.root).clear(imageView)
                    imageView.setImageDrawable(null)
                } else {
                    imageView.isVisible = true
                    Glide.with(binding.root)
                        .load(resolveImageUrl(imageUrl))
                        .centerCrop()
                        .into(imageView)
                }
            }
            binding.layoutThumbnails.isVisible = imageUrls.isNotEmpty()
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

        private fun resolveImageUrl(imageUrl: String): String {
            return if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                imageUrl
            } else {
                NetworkConfig.BASE_URL.trimEnd('/') + "/" + imageUrl.trimStart('/')
            }
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
