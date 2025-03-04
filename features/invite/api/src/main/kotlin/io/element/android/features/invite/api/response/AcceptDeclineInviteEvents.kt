/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.invite.api.response

interface AcceptDeclineInviteEvents {
    data class AcceptInvite(val invite: InviteData?) : AcceptDeclineInviteEvents
    data class DeclineInvite(val invite: InviteData?, val blockUser: Boolean = false) : AcceptDeclineInviteEvents
}
