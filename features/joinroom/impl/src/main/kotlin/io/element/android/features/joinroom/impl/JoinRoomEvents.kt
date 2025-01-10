/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.joinroom.impl

sealed interface JoinRoomEvents {
    data object RetryFetchingContent : JoinRoomEvents
    data object DismissContent : JoinRoomEvents
    data object JoinRoom : JoinRoomEvents
    data object KnockRoom : JoinRoomEvents
    data object ForgetRoom : JoinRoomEvents
    data class CancelKnock(val requiresConfirmation: Boolean) : JoinRoomEvents
    data class UpdateKnockMessage(val message: String) : JoinRoomEvents
    data object ClearActionStates : JoinRoomEvents
    data object AcceptInvite : JoinRoomEvents
    data object DeclineInvite : JoinRoomEvents
}
