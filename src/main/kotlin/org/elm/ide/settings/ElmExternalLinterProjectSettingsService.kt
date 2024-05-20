/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.settings

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

val Project.experimentalFlags: ElmExternalLinterProjectSettingsService
    get() = service<ElmExternalLinterProjectSettingsService>()

private const val SERVICE_NAME: String = "ElmExternalLinterProjectSettings"

@State(name = SERVICE_NAME, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ElmExternalLinterProjectSettingsService(
    project: Project
) : ElmProjectSettingsServiceBase<ElmExternalLinterProjectSettingsService.ElmExternalLinterProjectSettings>(project, ElmExternalLinterProjectSettings()) {
    val additionalArguments: String get() = state.additionalArguments
    val debugIntentionEnabled: Boolean get() = state.enableDebugIntention
    val extractVariableEnabled: Boolean get() = state.enableExtractVariable
    val elmReviewOnTheFlyEnabled: Boolean get() = state.enableElmReviewOnTheFly
    val wipFeaturesEnabled: Boolean get() = state.enableWipFeatures

    override fun noStateLoaded() {
        val unstableFlags = project.elmSettings
        state.additionalArguments = unstableFlags.state.externalLinterArguments
        state.enableDebugIntention = unstableFlags.state.enableDebugIntention
        state.enableElmReviewOnTheFly = unstableFlags.state.enableElmReviewOnTheFly
        state.enableWipFeatures = unstableFlags.state.enableWipFeatures
    }

    class ElmExternalLinterProjectSettings : ElmProjectSettingsBase<ElmExternalLinterProjectSettings>() {
        var additionalArguments by property("") { it.isEmpty() }

        @AffectsHighlighting
        var enableElmReviewOnTheFly by property(true)
        var enableDebugIntention by property(false)
        var enableExtractVariable by property(false)
        var enableWipFeatures by property(false)

        override fun copy(): ElmExternalLinterProjectSettings {
            val state = ElmExternalLinterProjectSettings()
            state.copyFrom(this)
            return state
        }
    }

    override fun createSettingsChangedEvent(
        oldEvent: ElmExternalLinterProjectSettings,
        newEvent: ElmExternalLinterProjectSettings
    ): SettingsChangedEvent = SettingsChangedEvent(oldEvent, newEvent)

    class SettingsChangedEvent(
        oldState: ElmExternalLinterProjectSettings,
        newState: ElmExternalLinterProjectSettings
    ) : SettingsChangedEventBase<ElmExternalLinterProjectSettings>(oldState, newState)
}
