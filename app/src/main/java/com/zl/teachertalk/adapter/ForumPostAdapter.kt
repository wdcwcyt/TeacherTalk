package com.zl.teachertalk.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zl.teachertalk.bean.ForumPost
import com.zl.teachertalk.databinding.ItemForumPostBinding

/**
 * 论坛帖子列表适配器。
 *
 * 当前卡片展示标题和服务端返回的正文预览。
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
         * 将帖子数据绑定到临时卡片。
         *
         * @param post 待展示的帖子
         */
        fun bind(post: ForumPost) {
            binding.tvPostTitle.text = post.title
            binding.tvPostPreview.text = post.contentPreview
        }
    }

    private companion object {
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
