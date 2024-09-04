/*
 * Copyright (c) 2023 New Vector Ltd
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

package io.element.android.features.messages.impl.forward

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.composable.Children
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.navigation.model.permanent.PermanentNavModel
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.ParentNode
import com.bumble.appyx.core.plugin.Plugin
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.element.android.anvilannotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.timeline.TimelineProvider
import io.element.android.libraries.roomselect.api.RoomSelectEntryPoint
import io.element.android.libraries.roomselect.api.RoomSelectMode
import kotlinx.parcelize.Parcelize

@ContributesNode(RoomScope::class)
class ForwardMessagesNode @AssistedInject constructor(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ForwardMessagesPresenter.Factory,
    private val roomSelectEntryPoint: RoomSelectEntryPoint,
) : ParentNode<ForwardMessagesNode.NavTarget>(
    navModel = PermanentNavModel(
        navTargets = setOf(NavTarget),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    @Parcelize
    object NavTarget : Parcelable

    interface Callback : Plugin {
        fun onForwardedToSingleRoom(roomId: RoomId)
    }

    data class Inputs(
        val eventId: EventId,
        val timelineProvider: TimelineProvider,
    ) : NodeInputs

    private val inputs = inputs<Inputs>()
    private val presenter = presenterFactory.create(inputs.eventId.value, inputs.timelineProvider)
    private val callbacks = plugins.filterIsInstance<Callback>()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        val callback = object : RoomSelectEntryPoint.Callback {
            override fun onRoomSelected(roomIds: List<RoomId>) {
                presenter.onRoomSelected(roomIds)
            }

            override fun onCancel() {
                navigateUp()
            }
        }

        return roomSelectEntryPoint.nodeBuilder(this, buildContext)
            .callback(callback)
            .params(RoomSelectEntryPoint.Params(mode = RoomSelectMode.Forward))
            .build()
    }

    @Composable
    override fun View(modifier: Modifier) {
        Box(modifier = modifier) {
            // Will render to room select screen
            Children(
                navModel = navModel,
            )

            val state = presenter.present()
            ForwardMessagesView(
                state = state,
                onForwardSuccess = ::onForwardSuccess,
            )
        }
    }

    private fun onForwardSuccess(roomIds: List<RoomId>) {
        navigateUp()
        if (roomIds.size == 1) {
            val targetRoomId = roomIds.first()
            callbacks.forEach { it.onForwardedToSingleRoom(targetRoomId) }
        }
    }
}
