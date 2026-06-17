package com.auso.social.network

import com.auso.social.network.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for AUSO backend
 */
interface AusoApi {

    // ========== AUTH ==========
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // ========== USERS ==========
    @GET("api/v1/users/me")
    suspend fun getMyProfile(@Header("Authorization") token: String): Response<UserProfileResponse>

    @PUT("api/v1/users/me")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<UserProfile>

    @GET("api/v1/users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<List<UserProfile>>

    @GET("api/v1/users/{username}")
    suspend fun getUserProfile(
        @Header("Authorization") token: String,
        @Path("username") username: String
    ): Response<UserProfileResponse>

    // ========== POSTS ==========
    @POST("api/v1/posts/text")
    suspend fun createTextPost(
        @Header("Authorization") token: String,
        @Body request: CreateTextPostRequest
    ): Response<Post>

    @POST("api/v1/posts/image-pack")
    suspend fun createImagePackPost(
        @Header("Authorization") token: String,
        @Body request: CreateImagePackPostRequest
    ): Response<Post>

    @GET("api/v1/posts/feed")
    suspend fun getFeed(
        @Header("Authorization") token: String,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<FeedResponse>

    @GET("api/v1/posts/{id}")
    suspend fun getPost(
        @Header("Authorization") token: String,
        @Path("id") postId: String
    ): Response<PostResponse>

    @DELETE("api/v1/posts/{id}")
    suspend fun deletePost(
        @Header("Authorization") token: String,
        @Path("id") postId: String
    ): Response<MessageResponse>

    @POST("api/v1/posts/{id}/like")
    suspend fun toggleLike(
        @Header("Authorization") token: String,
        @Path("id") postId: String
    ): Response<LikeResponse>

    @POST("api/v1/posts/poll")
    suspend fun createPoll(
        @Header("Authorization") token: String,
        @Body request: CreatePollRequest
    ): Response<PollPostResponse>

    @POST("api/v1/polls/{id}/vote")
    suspend fun votePoll(
        @Header("Authorization") token: String,
        @Path("id") pollId: String,
        @Body request: VoteRequest
    ): Response<PollResponse>

    // ========== COMMENTS ==========
    @POST("api/v1/posts/{postId}/comments")
    suspend fun createComment(
        @Header("Authorization") token: String,
        @Path("postId") postId: String,
        @Body request: CreateCommentRequest
    ): Response<CommentResponse>

    @GET("api/v1/posts/{postId}/comments")
    suspend fun getComments(
        @Header("Authorization") token: String,
        @Path("postId") postId: String
    ): Response<List<CommentResponse>>

    // ========== COMMUNITIES ==========
    @POST("api/v1/communities")
    suspend fun createCommunity(
        @Header("Authorization") token: String,
        @Body request: CreateCommunityRequest
    ): Response<CommunityResponse>

    @GET("api/v1/communities")
    suspend fun listCommunities(
        @Header("Authorization") token: String,
        @Query("q") query: String? = null
    ): Response<List<CommunityWithCount>>

    @POST("api/v1/communities/{id}/join")
    suspend fun joinCommunity(
        @Header("Authorization") token: String,
        @Path("id") communityId: String
    ): Response<MessageResponse>

    // ========== GROUPS ==========
    @POST("api/v1/groups")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): Response<GroupResponse>

    @GET("api/v1/groups")
    suspend fun listGroups(
        @Header("Authorization") token: String,
        @Query("q") query: String? = null
    ): Response<List<GroupWithCount>>

    @POST("api/v1/groups/{id}/join")
    suspend fun joinGroup(
        @Header("Authorization") token: String,
        @Path("id") groupId: String
    ): Response<MessageResponse>

    // ========== FOLLOWS ==========
    @POST("api/v1/users/{userId}/follow")
    suspend fun toggleFollow(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Response<FollowResponse>

    // ========== UPLOAD ==========
    @Multipart
    @POST("api/v1/users/me/profile-photo")
    suspend fun uploadProfilePhoto(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<UploadUrlResponse>

    @Multipart
    @POST("api/v1/users/me/cover-photo")
    suspend fun uploadCoverPhoto(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<UploadUrlResponse>

    @Multipart
    @POST("api/v1/posts/image")
    suspend fun createImagePost(
        @Header("Authorization") token: String,
        @Part files: List<MultipartBody.Part>
    ): Response<ImagePostResponse>

    @Multipart
    @POST("api/v1/posts/{postId}/images")
    suspend fun addImagesToPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: String,
        @Part files: List<MultipartBody.Part>
    ): Response<List<PostImage>>

    @Multipart
    @POST("api/v1/posts/video")
    suspend fun createVideoPost(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<VideoPostResponse>

    // ========== CHANNELS ==========
    @POST("api/v1/communities/{communityId}/channels")
    suspend fun createChannel(
        @Header("Authorization") token: String,
        @Path("communityId") communityId: String,
        @Body request: CreateChannelRequest
    ): Response<Channel>
}
