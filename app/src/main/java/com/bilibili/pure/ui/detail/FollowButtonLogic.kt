package com.bilibili.pure.ui.detail

enum class FollowAction {
    Follow,
    ConfirmUnfollow
}

fun unfollowNeedsConfirm(isFollowed: Boolean): Boolean = isFollowed

fun followButtonLabel(isFollowed: Boolean): String = if (isFollowed) "已关注" else "关注"

fun followActionForClick(isFollowed: Boolean): FollowAction =
    if (unfollowNeedsConfirm(isFollowed)) FollowAction.ConfirmUnfollow else FollowAction.Follow
