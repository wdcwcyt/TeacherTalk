package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ApiResponse
import com.zl.teachertalk.bean.CommentCreateRequest
import com.zl.teachertalk.bean.CreatedPost
import com.zl.teachertalk.bean.ForumComment
import com.zl.teachertalk.bean.ForumCommentPage
import com.zl.teachertalk.bean.ForumPage
import com.zl.teachertalk.bean.ForumPostDetail
import com.zl.teachertalk.bean.FavoriteState
import com.zl.teachertalk.bean.LikeState
import com.zl.teachertalk.bean.PostCreateRequest
import com.zl.teachertalk.bean.PostUpdateRequest
import com.zl.teachertalk.bean.UploadedImage
import com.zl.teachertalk.bean.UpdatedPost
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response

/**
 * 论坛数据仓库。
 *
 * 负责帖子分页、详情、点赞收藏、评论、图片上传和带图发帖接口。
 *
 * @param apiService 论坛与文件接口
 */
class ForumRepository(
    private val apiService: ApiService
) {

    /**
     * 加载一页帖子。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 帖子分页结果
     */
    suspend fun loadPosts(page: Int, size: Int): ForumPage {
        return withContext(Dispatchers.IO) {
            apiService.getPosts(page = page, size = size).requireData()
        }
    }

    /**
     * 加载一页当前用户发布的帖子。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 帖子分页结果
     */
    suspend fun loadMyPosts(page: Int, size: Int): ForumPage {
        return withContext(Dispatchers.IO) {
            apiService.getMyPosts(page = page, size = size).requireData()
        }
    }

    /**
     * 加载一页当前用户收藏的帖子。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 帖子分页结果
     */
    suspend fun loadFavoritePosts(page: Int, size: Int): ForumPage {
        return withContext(Dispatchers.IO) {
            apiService.getFavoritePosts(page = page, size = size).requireData()
        }
    }

    /**
     * 获取帖子详情。
     *
     * @param postId 帖子编号
     * @return 完整帖子详情
     */
    suspend fun loadPostDetail(postId: Long): ForumPostDetail {
        return withContext(Dispatchers.IO) {
            apiService.getPostDetail(postId).requireData()
        }
    }

    /**
     * 点赞帖子。
     *
     * @param postId 帖子编号
     * @return 点赞后的最新状态
     */
    suspend fun likePost(postId: Long): LikeState {
        return withContext(Dispatchers.IO) {
            apiService.likePost(postId).requireData()
        }
    }

    /**
     * 取消点赞帖子。
     *
     * @param postId 帖子编号
     * @return 取消点赞后的最新状态
     */
    suspend fun unlikePost(postId: Long): LikeState {
        return withContext(Dispatchers.IO) {
            apiService.unlikePost(postId).requireData()
        }
    }

    /**
     * 收藏帖子。
     *
     * @param postId 帖子编号
     * @return 收藏后的最新状态
     */
    suspend fun favoritePost(postId: Long): FavoriteState {
        return withContext(Dispatchers.IO) {
            apiService.favoritePost(postId).requireData()
        }
    }

    /**
     * 取消收藏帖子。
     *
     * @param postId 帖子编号
     * @return 取消收藏后的最新状态
     */
    suspend fun unfavoritePost(postId: Long): FavoriteState {
        return withContext(Dispatchers.IO) {
            apiService.unfavoritePost(postId).requireData()
        }
    }

    /**
     * 加载一页帖子评论。
     *
     * @param postId 帖子编号
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 评论分页结果
     */
    suspend fun loadComments(
        postId: Long,
        page: Int,
        size: Int
    ): ForumCommentPage {
        return withContext(Dispatchers.IO) {
            apiService.getPostComments(
                postId = postId,
                page = page,
                size = size
            ).requireData()
        }
    }

    /**
     * 发布帖子评论。
     *
     * @param postId 帖子编号
     * @param content 评论正文
     * @return 新评论
     */
    suspend fun createComment(postId: Long, content: String): ForumComment {
        return withContext(Dispatchers.IO) {
            apiService.createPostComment(
                postId = postId,
                request = CommentCreateRequest(content)
            ).requireData()
        }
    }

    /**
     * 上传一张压缩后的 JPEG 图片。
     *
     * @param file 客户端压缩后的临时文件
     * @return 原图和缩略图地址
     */
    suspend fun uploadImage(file: File): UploadedImage {
        return withContext(Dispatchers.IO) {
            val requestBody = file.asRequestBody(JPEG_MEDIA_TYPE.toMediaType())
            val filePart = MultipartBody.Part.createFormData(
                name = MULTIPART_FILE_FIELD,
                filename = file.name,
                body = requestBody
            )
            apiService.uploadImage(filePart).requireData()
        }
    }

    /**
     * 发布带图帖子。
     *
     * @param title 标题；没有标题时为 null
     * @param content 正文
     * @param imageUrls 原图地址，按选择顺序排列
     * @return 新帖子
     */
    suspend fun createPost(
        title: String?,
        content: String,
        imageUrls: List<String>
    ): CreatedPost {
        return withContext(Dispatchers.IO) {
            apiService.createPost(
                PostCreateRequest(
                    title = title,
                    content = content,
                    imageUrls = imageUrls
                )
            ).requireData()
        }
    }

    /**
     * 编辑自己的帖子，标题、正文和图片按提交内容整体替换。
     *
     * @param postId 帖子编号
     * @param title 编辑后的标题；没有标题时为 null
     * @param content 编辑后的正文
     * @param imageUrls 编辑后的完整图片地址列表
     * @return 帖子编号
     */
    suspend fun updatePost(
        postId: Long,
        title: String?,
        content: String,
        imageUrls: List<String>
    ): UpdatedPost {
        return withContext(Dispatchers.IO) {
            apiService.updatePost(
                postId = postId,
                request = PostUpdateRequest(
                    title = title,
                    content = content,
                    imageUrls = imageUrls
                )
            ).requireData()
        }
    }

    private fun <T> Response<ApiResponse<T>>.requireData(): T {
        val responseBody = body()
        if (!isSuccessful || responseBody == null || responseBody.code != 0) {
            throw ApiException(
                code = responseBody?.code ?: code(),
                message = responseBody?.message?.takeIf { it.isNotBlank() } ?: message(),
                httpStatus = code()
            )
        }
        return responseBody.data ?: throw ApiException(
            code = UNKNOWN_RESPONSE_CODE,
            message = "Response data is empty",
            httpStatus = code()
        )
    }

    private companion object {
        const val JPEG_MEDIA_TYPE = "image/jpeg"
        const val MULTIPART_FILE_FIELD = "file"
        const val UNKNOWN_RESPONSE_CODE = -1
    }
}
