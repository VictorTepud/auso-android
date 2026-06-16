package com.auso.social.network.model

import com.google.gson.annotations.SerializedName

// ========== AUTH ==========
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: UserProfile
)

// ========== USER ==========
data class UserProfile(
    val id: String,
    val username: String,
    @SerializedName("display_name") val displayName: String = "",
    val bio: String = "",
    @SerializedName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerializedName("cover_photo_url") val coverPhotoUrl: String? = null,
    val phone: String = "",
    val location: String = "",
    val website: String = "",
    @SerializedName("birth_date") val birthDate: String? = null,
    val gender: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class UserProfileResponse(
    val user: UserProfile,
    val stats: UserStats
)

data class UserStats(
    @SerializedName("posts_count") val postsCount: Long = 0,
    @SerializedName("followers_count") val followersCount: Long = 0,
    @SerializedName("following_count") val followingCount: Long = 0,
    @SerializedName("communities_count") val communitiesCount: Long = 0,
    @SerializedName("groups_count") val groupsCount: Long = 0
)

data class UpdateProfileRequest(
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val website: String? = null,
    @SerializedName("birth_date") val birthDate: String? = null,
    val gender: String? = null
)

// ========== POSTS ==========
data class Post(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("community_id") val communityId: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("group_id") val groupId: String? = null,
    @SerializedName("post_type") val postType: String,
    val content: String = "",
    @SerializedName("background_color") val backgroundColor: String? = null,
    @SerializedName("background_image_url") val backgroundImageUrl: String? = null,
    @SerializedName("layout_mode") val layoutMode: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class PostImage(
    val id: String,
    @SerializedName("post_id") val postId: String,
    @SerializedName("image_url") val imageUrl: String,
    val position: Int,
    @SerializedName("created_at") val createdAt: String = ""
)

data class PostVideo(
    val id: String,
    @SerializedName("post_id") val postId: String,
    @SerializedName("original_filename") val originalFilename: String = "",
    @SerializedName("hls_master_playlist_url") val hlsMasterPlaylistUrl: String = "",
    val duration: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("created_at") val createdAt: String = ""
)

data class PostResponse(
    val post: Post,
    val images: List<PostImage> = emptyList(),
    val video: PostVideo? = null,
    val poll: PollResponse? = null,
    @SerializedName("likes_count") val likesCount: Long = 0,
    @SerializedName("comments_count") val commentsCount: Long = 0,
    @SerializedName("is_liked") val isLiked: Boolean = false,
    @SerializedName("author_username") val authorUsername: String = "",
    @SerializedName("author_display_name") val authorDisplayName: String = "",
    @SerializedName("author_profile_photo") val authorProfilePhoto: String? = null
)

data class FeedResponse(
    val posts: List<PostResponse>,
    val page: Int,
    val limit: Int
)

data class CreateTextPostRequest(
    val content: String,
    @SerializedName("background_color") val backgroundColor: String? = null,
    @SerializedName("background_image_url") val backgroundImageUrl: String? = null,
    @SerializedName("community_id") val communityId: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("group_id") val groupId: String? = null
)

data class CreateImagePackPostRequest(
    val content: String? = null,
    @SerializedName("layout_mode") val layoutMode: String? = "carousel",
    @SerializedName("community_id") val communityId: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("group_id") val groupId: String? = null
)

data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    @SerializedName("allows_multiple_answers") val allowsMultipleAnswers: Boolean? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("community_id") val communityId: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("group_id") val groupId: String? = null
)

data class PollPostResponse(
    val post: Post,
    val poll: PollResponse
)

// ========== POLLS ==========
data class PollResponse(
    val poll: Poll,
    val options: List<PollOptionResponse>,
    @SerializedName("total_votes") val totalVotes: Long,
    @SerializedName("user_has_voted") val userHasVoted: Boolean
)

data class Poll(
    val id: String,
    @SerializedName("post_id") val postId: String,
    val question: String,
    @SerializedName("allows_multiple_answers") val allowsMultipleAnswers: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("created_at") val createdAt: String = ""
)

data class PollOptionResponse(
    val option: PollOption,
    @SerializedName("votes_count") val votesCount: Long,
    @SerializedName("has_voted") val hasVoted: Boolean
)

data class PollOption(
    val id: String,
    @SerializedName("poll_id") val pollId: String,
    @SerializedName("option_text") val optionText: String,
    val position: Int,
    @SerializedName("created_at") val createdAt: String = ""
)

data class VoteRequest(
    @SerializedName("option_ids") val optionIds: List<String>
)

// ========== LIKES ==========
data class LikeResponse(
    val liked: Boolean,
    @SerializedName("likes_count") val likesCount: Long
)

// ========== COMMENTS ==========
data class CreateCommentRequest(
    val content: String,
    @SerializedName("parent_comment_id") val parentCommentId: String? = null
)

data class CommentResponse(
    val comment: Comment,
    @SerializedName("author_username") val authorUsername: String,
    @SerializedName("author_display_name") val authorDisplayName: String,
    @SerializedName("author_profile_photo") val authorProfilePhoto: String? = null
)

data class Comment(
    val id: String,
    @SerializedName("post_id") val postId: String,
    @SerializedName("user_id") val userId: String,
    val content: String,
    @SerializedName("parent_comment_id") val parentCommentId: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

// ========== COMMUNITIES ==========
data class CreateCommunityRequest(
    val name: String,
    val description: String? = null,
    @SerializedName("is_private") val isPrivate: Boolean? = null
)

data class CommunityResponse(
    val community: Community,
    @SerializedName("members_count") val membersCount: Long,
    @SerializedName("is_member") val isMember: Boolean,
    @SerializedName("user_role") val userRole: String? = null
)

data class Community(
    val id: String,
    val name: String,
    val description: String = "",
    @SerializedName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerializedName("creator_id") val creatorId: String,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class CommunityWithCount(
    val id: String,
    val name: String,
    val description: String = "",
    @SerializedName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerializedName("creator_id") val creatorId: String,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("members_count") val membersCount: Long = 0
)

// ========== GROUPS ==========
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    @SerializedName("is_private") val isPrivate: Boolean? = null
)

data class GroupResponse(
    val group: Group,
    @SerializedName("members_count") val membersCount: Long,
    @SerializedName("is_member") val isMember: Boolean,
    @SerializedName("user_role") val userRole: String? = null
)

data class Group(
    val id: String,
    val name: String,
    val description: String = "",
    @SerializedName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerializedName("creator_id") val creatorId: String,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class GroupWithCount(
    val id: String,
    val name: String,
    val description: String = "",
    @SerializedName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerializedName("creator_id") val creatorId: String,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("members_count") val membersCount: Long = 0
)

// ========== CHANNELS ==========
data class CreateChannelRequest(
    val name: String,
    val description: String? = null
)

data class Channel(
    val id: String,
    @SerializedName("community_id") val communityId: String,
    val name: String,
    val description: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

// ========== FOLLOWS ==========
data class FollowResponse(
    val following: Boolean
)

// ========== GENERIC ==========
data class MessageResponse(
    val message: String
)

data class ApiError(
    val status: Int,
    val message: String
)

// ========== UPLOAD ==========
data class UploadUrlResponse(
    val url: String
)

data class ImagePostResponse(
    val post: Post,
    val images: List<PostImage> = emptyList()
)
