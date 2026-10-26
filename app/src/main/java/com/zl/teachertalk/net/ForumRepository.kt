package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ApiResponse
import com.zl.teachertalk.bean.CreatedPost
import com.zl.teachertalk.bean.ForumPage
import com.zl.teachertalk.bean.PostCreateRequest
import com.zl.teachertalk.bean.UploadedImage
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
 * 负责帖子分页、图片上传和带图发帖接口。
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
