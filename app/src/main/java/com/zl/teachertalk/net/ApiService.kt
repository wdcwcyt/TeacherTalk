package com.zl.teachertalk.net

import com.zl.teachertalk.bean.ApiResponse
import com.zl.teachertalk.bean.AuthResult
import com.zl.teachertalk.bean.AvatarResponse
import com.zl.teachertalk.bean.CancelAccountRequest
import com.zl.teachertalk.bean.CreatedPost
import com.zl.teachertalk.bean.CommentCreateRequest
import com.zl.teachertalk.bean.ForumPage
import com.zl.teachertalk.bean.ForumComment
import com.zl.teachertalk.bean.ForumCommentPage
import com.zl.teachertalk.bean.ForumPostDetail
import com.zl.teachertalk.bean.FavoriteState
import com.zl.teachertalk.bean.LikeState
import com.zl.teachertalk.bean.LoginRequest
import com.zl.teachertalk.bean.PostCreateRequest
import com.zl.teachertalk.bean.PostUpdateRequest
import com.zl.teachertalk.bean.RegisterRequest
import com.zl.teachertalk.bean.SendSmsCodeRequest
import com.zl.teachertalk.bean.SendSmsCodeResponse
import com.zl.teachertalk.bean.UploadedImage
import com.zl.teachertalk.bean.UpdatedPost
import com.zl.teachertalk.bean.UserInfoBean
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * 服务端接口声明。
 *
 * 使用 `Response<ApiResponse<T>>` 可以同时拿到 HTTP 状态和统一响应体，所有接口都通过挂起函数调用。
 */
interface ApiService {

    @POST("api/auth/sms-code")
    suspend fun sendSmsCode(
        @Body request: SendSmsCodeRequest
    ): Response<ApiResponse<SendSmsCodeResponse>>

    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<AuthResult>>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<AuthResult>>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<ApiResponse<UserInfoBean>>

    @POST("api/users/me/cancel")
    suspend fun cancelCurrentUserAccount(
        @Body request: CancelAccountRequest
    ): Response<ApiResponse<Any>>

    @Multipart
    @PUT("api/users/me/avatar")
    suspend fun updateCurrentUserAvatar(
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<AvatarResponse>>

    @GET("api/posts")
    suspend fun getPosts(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<ForumPage>>

    @GET("api/posts/mine")
    suspend fun getMyPosts(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<ForumPage>>

    @GET("api/posts/favorites")
    suspend fun getFavoritePosts(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<ForumPage>>

    @GET("api/posts/{id}")
    suspend fun getPostDetail(
        @Path("id") postId: Long
    ): Response<ApiResponse<ForumPostDetail>>

    @PUT("api/posts/{id}")
    suspend fun updatePost(
        @Path("id") postId: Long,
        @Body request: PostUpdateRequest
    ): Response<ApiResponse<UpdatedPost>>

    @POST("api/posts/{id}/like")
    suspend fun likePost(
        @Path("id") postId: Long
    ): Response<ApiResponse<LikeState>>

    @DELETE("api/posts/{id}/like")
    suspend fun unlikePost(
        @Path("id") postId: Long
    ): Response<ApiResponse<LikeState>>

    @POST("api/posts/{id}/favorite")
    suspend fun favoritePost(
        @Path("id") postId: Long
    ): Response<ApiResponse<FavoriteState>>

    @DELETE("api/posts/{id}/favorite")
    suspend fun unfavoritePost(
        @Path("id") postId: Long
    ): Response<ApiResponse<FavoriteState>>

    @GET("api/posts/{id}/comments")
    suspend fun getPostComments(
        @Path("id") postId: Long,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<ForumCommentPage>>

    @POST("api/posts/{id}/comments")
    suspend fun createPostComment(
        @Path("id") postId: Long,
        @Body request: CommentCreateRequest
    ): Response<ApiResponse<ForumComment>>

    @Multipart
    @POST("api/files")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<UploadedImage>>

    @POST("api/posts")
    suspend fun createPost(
        @Body request: PostCreateRequest
    ): Response<ApiResponse<CreatedPost>>
}
