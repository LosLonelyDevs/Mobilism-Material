package org.mobilism.material.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ForumCommentGateway
import org.mobilism.material.data.ReleaseComment

internal typealias ForumCommentAction = org.mobilism.material.data.ForumCommentAction
internal typealias ForumCommentTarget = org.mobilism.material.data.ForumCommentTarget
internal val LocalCommentGateway = staticCompositionLocalOf<ForumCommentGateway?> { null }

internal fun canManageComment(comment: ReleaseComment, auth: AuthState): Boolean =
    comment.id > 0 && auth is AuthState.SignedIn && auth.username.isNotBlank() &&
        comment.author.equals(auth.username, ignoreCase = true)
