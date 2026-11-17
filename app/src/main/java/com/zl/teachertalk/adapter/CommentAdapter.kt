package com.zl.teachertalk.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zl.teachertalk.R
import com.zl.teachertalk.bean.ForumComment
import com.zl.teachertalk.databinding.ItemCommentBinding
import com.zl.teachertalk.net.NetworkConfig
import com.zl.teachertalk.utils.ForumTimeFormatter

/**
 * 帖子详情页评论列表适配器。
 */
class CommentAdapter : ListAdapter<ForumComment, CommentAdapter.CommentViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 单条评论的 ViewHolder。
     */
    class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * 将评论数据绑定到条目。
         *
         * @param comment 评论数据
         */
        fun bind(comment: ForumComment) {
            val context = binding.root.context
            binding.tvCommentAuthor.text = comment.authorNickname.trim().ifEmpty {
                context.getString(R.string.forum_comment_unknown_author)
            }
            binding.tvCommentContent.text = comment.content
            binding.tvCommentTime.text = ForumTimeFormatter.format(comment.createdAt)
            val avatarUrl = comment.authorAvatarUrl?.trim().orEmpty()
            if (avatarUrl.isEmpty()) {
                Glide.with(binding.root).clear(binding.ivCommentAvatar)
                binding.ivCommentAvatar.setImageResource(R.drawable.icon_user_avatar)
                return
            }

            Glide.with(binding.root)
                .load(NetworkConfig.resolveMediaUrl(avatarUrl))
                .placeholder(R.drawable.icon_user_avatar)
                .error(R.drawable.icon_user_avatar)
                .centerCrop()
                .into(binding.ivCommentAvatar)
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ForumComment>() {
            override fun areItemsTheSame(
                oldItem: ForumComment,
                newItem: ForumComment
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: ForumComment,
                newItem: ForumComment
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
