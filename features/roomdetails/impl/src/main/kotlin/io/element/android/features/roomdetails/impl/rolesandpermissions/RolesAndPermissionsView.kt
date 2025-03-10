/*
 * Copyright (c) 2024 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.roomdetails.impl.rolesandpermissions

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.roomdetails.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.sheetStateForPreview
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.hide
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun RolesAndPermissionsView(
    state: RolesAndPermissionsState,
    roomDetailsAdminSettingsNavigator: RoomDetailsAdminSettingsNavigator,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        title = stringResource(R.string.screen_room_roles_and_permissions_title),
        onBackPressed = roomDetailsAdminSettingsNavigator::onBackPressed,
    ) {
        ListSectionHeader(title = stringResource(R.string.screen_room_roles_and_permissions_roles_header), hasDivider = false)
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_room_roles_and_permissions_admins)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Admin())),
            trailingContent = ListItemContent.Text("${state.adminCount}"),
            onClick = { roomDetailsAdminSettingsNavigator.openAdminList() },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_room_roles_and_permissions_moderators)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChatProblem())),
            trailingContent = ListItemContent.Text("${state.moderatorCount}"),
            onClick = { roomDetailsAdminSettingsNavigator.openModeratorList() },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_room_roles_and_permissions_change_my_role)) },
            onClick = { state.eventSink(RolesAndPermissionsEvents.ChangeOwnRole) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit()))
        )
        HorizontalDivider()
    }

    when (state.changeOwnRoleAction) {
        is AsyncAction.Confirming -> {
            ChangeOwnRoleBottomSheet(
                eventSink = state.eventSink,
            )
        }
        is AsyncAction.Loading -> {
            ProgressDialog()
        }
        is AsyncAction.Failure -> {
            ErrorDialog(
                content = stringResource(CommonStrings.error_unknown),
                onDismiss = { state.eventSink(RolesAndPermissionsEvents.CancelPendingAction) }
            )
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeOwnRoleBottomSheet(
    eventSink: (RolesAndPermissionsEvents) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = if (LocalInspectionMode.current) {
        sheetStateForPreview()
    } else {
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    }
    fun dismiss() {
        sheetState.hide(coroutineScope) {
            eventSink(RolesAndPermissionsEvents.CancelPendingAction)
        }
    }
    ModalBottomSheet(
        modifier = Modifier
            .systemBarsPadding()
            .navigationBarsPadding(),
        sheetState = sheetState,
        onDismissRequest = ::dismiss,
    ) {
        Text(
            modifier = Modifier.padding(14.dp),
            text = stringResource(R.string.screen_room_roles_and_permissions_change_my_role),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
        Text(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            text = stringResource(R.string.screen_room_change_role_confirm_demote_self_description),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_room_roles_and_permissions_change_role_demote_to_moderator)) },
            onClick = {
                sheetState.hide(coroutineScope) {
                    eventSink(RolesAndPermissionsEvents.DemoteSelfTo(RoomMember.Role.MODERATOR))
                }
            },
            style = ListItemStyle.Destructive,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_room_roles_and_permissions_change_role_demote_to_member)) },
            onClick = {
                sheetState.hide(coroutineScope) {
                    eventSink(RolesAndPermissionsEvents.DemoteSelfTo(RoomMember.Role.USER))
                }
            },
            style = ListItemStyle.Destructive,
        )
        ListItem(
            headlineContent = { Text(stringResource(CommonStrings.action_cancel)) },
            onClick = {
                sheetState.hide(coroutineScope) {
                    eventSink(RolesAndPermissionsEvents.CancelPendingAction)
                }
            },
            style = ListItemStyle.Primary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun RoomDetailsAdminSettingsViewPreview(@PreviewParameter(RolesAndPermissionsStateProvider::class) state: RolesAndPermissionsState) {
    ElementPreview {
        RolesAndPermissionsView(
            state = state,
            roomDetailsAdminSettingsNavigator = object : RoomDetailsAdminSettingsNavigator {},
        )
    }
}
