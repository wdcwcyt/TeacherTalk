package com.zl.teachertalk.adapter

import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.zl.teachertalk.R
import com.zl.teachertalk.databinding.ItemPostImagePreviewBinding

/**
 * 全屏图片预览分页适配器。
 *
 * 每页独立加载一张图片，避免切换时先清空当前页造成黑屏。
 *
 * @param imageUris 待预览图片
 * @param onImageClick 点击任意预览图时的回调
 */
class PostImagePreviewAdapter(
    private val imageUris: List<Uri>,
    private val onImageClick: () -> Unit
) : RecyclerView.Adapter<PostImagePreviewAdapter.PreviewViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val binding = ItemPostImagePreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PreviewViewHolder(binding)
    }

    override fun getItemCount(): Int = imageUris.size

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        holder.bind(
            imageUri = imageUris[position],
            imagePosition = position,
            totalCount = imageUris.size,
            onImageClick = onImageClick
        )
    }

    override fun onViewRecycled(holder: PreviewViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    /**
     * 单页图片预览的 ViewHolder。
     */
    class PreviewViewHolder(
        private val binding: ItemPostImagePreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * 加载当前页图片并更新加载状态。
         *
         * @param imageUri 图片地址
         * @param imagePosition 当前图片位置，从 0 开始
         * @param totalCount 图片总数
         * @param onImageClick 点击图片时的回调
         */
        fun bind(
            imageUri: Uri,
            imagePosition: Int,
            totalCount: Int,
            onImageClick: () -> Unit
        ) {
            binding.root.setOnClickListener {
                onImageClick()
            }
            binding.ivPreviewImage.contentDescription = binding.root.context.getString(
                R.string.post_image_preview_position,
                imagePosition + 1,
                totalCount
            )
            binding.progressPreview.isVisible = true
            binding.tvPreviewError.isVisible = false

            Glide.with(binding.root)
                .clear(binding.ivPreviewImage)
            binding.ivPreviewImage.setImageDrawable(null)
            Glide.with(binding.root)
                .load(imageUri)
                .fitCenter()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        exception: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "Failed to load preview image", exception)
                        binding.progressPreview.isVisible = false
                        binding.tvPreviewError.isVisible = true
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.progressPreview.isVisible = false
                        binding.tvPreviewError.isVisible = false
                        return false
                    }
                })
                .into(binding.ivPreviewImage)
        }

        /**
         * 回收页面时释放图片资源和监听器。
         */
        fun clear() {
            binding.root.setOnClickListener(null)
            Glide.with(binding.root).clear(binding.ivPreviewImage)
            binding.ivPreviewImage.setImageDrawable(null)
            binding.progressPreview.isVisible = false
            binding.tvPreviewError.isVisible = false
        }
    }

    private companion object {
        const val TAG = "PostImagePreviewAdapter"
    }
}
