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
//import org.elm.settings.ElmExternalLinterProjectSettingsService.ElmExternalLinterProjectSettings
//import org.elm.cargo.toolchain.ExternalLinter
//import org.elm.cargo.toolchain.RustChannel

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

    override fun noStateLoaded() {
        val unstableFlags = project.rustSettings
        state.additionalArguments = unstableFlags.state.externalLinterArguments
        state.enableDebugIntention = unstableFlags.state.enableDebugIntention
    }

    class ElmExternalLinterProjectSettings : ElmProjectSettingsBase<ElmExternalLinterProjectSettings>() {
//        @AffectsHighlighting
        var additionalArguments by property("") { it.isEmpty() }

//        @AffectsHighlighting
//        var channel by enum(RustChannel.DEFAULT)
//        @AffectsHighlighting
//        var envs by map<String, String>()
//        @AffectsHighlighting
        var enableDebugIntention by property(false)
        var enableExtractVariable by property(false)

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
