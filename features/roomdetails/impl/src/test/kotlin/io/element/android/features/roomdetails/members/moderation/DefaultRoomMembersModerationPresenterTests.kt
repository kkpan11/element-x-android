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

package io.element.android.features.roomdetails.members.moderation

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.roomdetails.impl.members.aRoomMember
import io.element.android.features.roomdetails.impl.members.aVictor
import io.element.android.features.roomdetails.impl.members.moderation.DefaultRoomMembersModerationPresenter
import io.element.android.features.roomdetails.impl.members.moderation.ModerationAction
import io.element.android.features.roomdetails.impl.members.moderation.RoomMembersModerationEvents
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.room.MatrixRoomMembersState
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.room.FakeMatrixRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultRoomMembersModerationPresenterTests {
    @Test
    fun `canDisplayModerationActions - when feature flag is disabled returns false`() = runTest {
        val featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.RoomModeration.key to false))
        val presenter = createDefaultRoomMembersModerationPresenter(featureFlagService = featureFlagService)
        assertThat(presenter.canDisplayModerationActions()).isFalse()
    }

    @Test
    fun `canDisplayModerationActions - when room is DM is false`() = runTest {
        val room = FakeMatrixRoom(isDirect = true, isPublic = true, isOneToOne = true).apply {
            givenRoomInfo(aRoomInfo(isDirect = true, isPublic = false, activeMembersCount = 2))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        assertThat(presenter.canDisplayModerationActions()).isFalse()
    }

    @Test
    fun `canDisplayModerationActions - when user can kick other users, FF is enabled and room is not a DM returns true`() = runTest {
        val room = FakeMatrixRoom(isDirect = false, isOneToOne = false).apply {
            givenCanKickResult(Result.success(true))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        assertThat(presenter.canDisplayModerationActions()).isTrue()
    }

    @Test
    fun `canDisplayModerationActions - when user can ban other users, FF is enabled and room is not a DM returns true`() = runTest {
        val room = FakeMatrixRoom(isDirect = false, isOneToOne = false).apply {
            givenCanBanResult(Result.success(true))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        assertThat(presenter.canDisplayModerationActions()).isTrue()
    }

    @Test
    fun `present - SelectRoomMember when the current user has permissions displays member actions`() = runTest {
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenUserRoleResult(Result.success(RoomMember.Role.ADMIN))
        }
        val selectedMember = aVictor()
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(selectedMember))
            with(awaitItem()) {
                assertThat(this.selectedRoomMember).isNotNull()
                assertThat(this.selectedRoomMember?.userId).isEqualTo(selectedMember.userId)
                assertThat(actions).containsExactly(
                    ModerationAction.DisplayProfile(selectedMember.userId),
                    ModerationAction.KickUser(selectedMember.userId),
                    ModerationAction.BanUser(selectedMember.userId)
                )
            }
        }
    }

    @Test
    fun `present - SelectRoomMember displays only view profile if selected member has same power level as the current user`() = runTest {
        val room = FakeMatrixRoom(sessionId = A_USER_ID).apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenUserRoleResult(Result.success(RoomMember.Role.ADMIN))
        }
        val selectedMember = aRoomMember(A_USER_ID_2, powerLevel = 100L)
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(selectedMember))
            with(awaitItem()) {
                assertThat(this.selectedRoomMember).isNotNull()
                assertThat(this.selectedRoomMember?.userId).isEqualTo(selectedMember.userId)
                assertThat(actions).containsExactly(
                    ModerationAction.DisplayProfile(selectedMember.userId),
                )
            }
        }
    }

    @Test
    fun `present - SelectRoomMember displays an unban confirmation dialog when the member is banned`() = runTest {
        val selectedMember = aRoomMember(A_USER_ID_2, membership = RoomMembershipState.BAN)
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenUserRoleResult(Result.success(RoomMember.Role.ADMIN))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(selectedMember))
            with(awaitItem()) {
                assertThat(selectedRoomMember).isNotNull()
                assertThat(unbanUserAsyncAction).isEqualTo(AsyncAction.Confirming)
            }
        }
    }

    @Test
    fun `present - Kick removes the user`() = runTest {
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenUserRoleResult(Result.success(RoomMember.Role.ADMIN))
        }
        val selectedMember = aVictor()
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(selectedMember))
            awaitItem().eventSink(RoomMembersModerationEvents.KickUser)
            skipItems(1)
            assertThat(awaitItem().actions).isEmpty()
            assertThat(awaitItem().kickUserAsyncAction).isEqualTo(AsyncAction.Loading)
            with(awaitItem()) {
                assertThat(kickUserAsyncAction).isEqualTo(AsyncAction.Success(Unit))
                assertThat(selectedRoomMember).isNull()
            }
        }
    }

    @Test
    fun `present - BanUser requires confirmation and then bans the user`() = runTest {
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenUserRoleResult(Result.success(RoomMember.Role.ADMIN))
        }
        val selectedMember = aVictor()
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(selectedMember))
            awaitItem().eventSink(RoomMembersModerationEvents.BanUser)
            val confirmingState = awaitItem()
            assertThat(confirmingState.banUserAsyncAction).isEqualTo(AsyncAction.Confirming)

            // Confirm
            confirmingState.eventSink(RoomMembersModerationEvents.BanUser)
            skipItems(1)
            assertThat(awaitItem().actions).isEmpty()
            assertThat(awaitItem().banUserAsyncAction).isEqualTo(AsyncAction.Loading)
            with(awaitItem()) {
                assertThat(banUserAsyncAction).isEqualTo(AsyncAction.Success(Unit))
                assertThat(selectedRoomMember).isNull()
            }
        }
    }

    @Test
    fun `present - UnbanUser requires confirmation and then unbans the user`() = runTest {
        val selectedMember = aRoomMember(A_USER_ID_2, membership = RoomMembershipState.BAN)
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenRoomMembersState(MatrixRoomMembersState.Ready(persistentListOf(selectedMember)))
            givenUserRoleResult(Result.success(RoomMember.Role.ADMIN))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            // Displays confirmation dialog
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(selectedMember))
            // Confirms unban
            awaitItem().eventSink(RoomMembersModerationEvents.UnbanUser)
            assertThat(awaitItem().actions).isEmpty()
            assertThat(awaitItem().unbanUserAsyncAction).isEqualTo(AsyncAction.Loading)
            with(awaitItem()) {
                assertThat(unbanUserAsyncAction).isEqualTo(AsyncAction.Success(Unit))
                assertThat(selectedRoomMember).isNull()
            }
        }
    }

    @Test
    fun `present - Reset removes the selected user and actions`() = runTest {
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(1)
            // Displays confirmation dialog
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(aVictor()))
            // Reset state
            awaitItem().eventSink(RoomMembersModerationEvents.Reset)
            assertThat(awaitItem().selectedRoomMember).isNull()
            assertThat(awaitItem().actions).isEmpty()
        }
    }

    @Test
    fun `present - Reset resets any async actions`() = runTest {
        val room = FakeMatrixRoom().apply {
            givenCanKickResult(Result.success(true))
            givenCanBanResult(Result.success(true))
            givenKickUserResult(Result.failure(Throwable("Eek")))
            givenBanUserResult(Result.failure(Throwable("Eek")))
            givenUnbanUserResult(Result.failure(Throwable("Eek")))
        }
        val presenter = createDefaultRoomMembersModerationPresenter(matrixRoom = room)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialItem = awaitItem()
            // Kick user and fail
            awaitItem().eventSink(RoomMembersModerationEvents.SelectRoomMember(aVictor()))
            awaitItem().eventSink(RoomMembersModerationEvents.KickUser)
            skipItems(2)
            assertThat(awaitItem().kickUserAsyncAction).isInstanceOf(AsyncAction.Loading::class.java)
            assertThat(awaitItem().kickUserAsyncAction).isInstanceOf(AsyncAction.Failure::class.java)
            // Reset it
            initialItem.eventSink(RoomMembersModerationEvents.Reset)
            assertThat(awaitItem().kickUserAsyncAction).isEqualTo(AsyncAction.Uninitialized)

            // Ban user and fail
            initialItem.eventSink(RoomMembersModerationEvents.SelectRoomMember(aVictor()))
            awaitItem().eventSink(RoomMembersModerationEvents.BanUser)
            awaitItem().eventSink(RoomMembersModerationEvents.BanUser)
            skipItems(2)
            assertThat(awaitItem().banUserAsyncAction).isInstanceOf(AsyncAction.Loading::class.java)
            assertThat(awaitItem().banUserAsyncAction).isInstanceOf(AsyncAction.Failure::class.java)
            // Reset it
            initialItem.eventSink(RoomMembersModerationEvents.Reset)
            assertThat(awaitItem().banUserAsyncAction).isEqualTo(AsyncAction.Uninitialized)

            // Unban user and fail
            initialItem.eventSink(RoomMembersModerationEvents.SelectRoomMember(aVictor().copy(membership = RoomMembershipState.BAN)))
            val confirmingState = awaitItem()
            assertThat(confirmingState.unbanUserAsyncAction).isInstanceOf(AsyncAction.Confirming::class.java)
            confirmingState.eventSink(RoomMembersModerationEvents.UnbanUser)
            skipItems(1)
            assertThat(awaitItem().unbanUserAsyncAction).isInstanceOf(AsyncAction.Loading::class.java)
            assertThat(awaitItem().unbanUserAsyncAction).isInstanceOf(AsyncAction.Failure::class.java)
            // Reset it
            initialItem.eventSink(RoomMembersModerationEvents.Reset)
            assertThat(awaitItem().unbanUserAsyncAction).isEqualTo(AsyncAction.Uninitialized)
        }
    }

    private fun TestScope.createDefaultRoomMembersModerationPresenter(
        matrixRoom: FakeMatrixRoom = FakeMatrixRoom(),
        featureFlagService: FakeFeatureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.RoomModeration.key to true)),
        dispatchers: CoroutineDispatchers = testCoroutineDispatchers(),
    ): DefaultRoomMembersModerationPresenter {
        return DefaultRoomMembersModerationPresenter(
            room = matrixRoom,
            featureFlagService = featureFlagService,
            dispatchers = dispatchers,
        )
    }
}
