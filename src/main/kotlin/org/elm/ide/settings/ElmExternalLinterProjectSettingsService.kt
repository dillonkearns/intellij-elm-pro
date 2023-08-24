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

val Project.externalLinterSettings: ElmExternalLinterProjectSettingsService
    get() = service<ElmExternalLinterProjectSettingsService>()

private const val SERVICE_NAME: String = "ElmExternalLinterProjectSettings"

@State(name = SERVICE_NAME, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ElmExternalLinterProjectSettingsService(
    project: Project
) : ElmProjectSettingsServiceBase<ElmExternalLinterProjectSettingsService.ElmExternalLinterProjectSettings>(project, ElmExternalLinterProjectSettings()) {
//    val tool: ExternalLinter get() = state.tool
    val additionalArguments: String get() = state.additionalArguments
//    val channel: RustChannel get() = state.channel
    val envs: Map<String, String> get() = state.envs
    val runOnTheFly: Boolean get() = state.runOnTheFly

    override fun noStateLoaded() {
        val rustSettings = project.rustSettings
//        state.tool = rustSettings.state.externalLinter
//        rustSettings.state.externalLinter = ExternalLinter.DEFAULT
        state.additionalArguments = rustSettings.state.externalLinterArguments
//        rustSettings.state.externalLinterArguments = ""
        state.runOnTheFly = rustSettings.state.runExternalLinterOnTheFly
//        rustSettings.state.runExternalLinterOnTheFly = false
    }

    class ElmExternalLinterProjectSettings : ElmProjectSettingsBase<ElmExternalLinterProjectSettings>() {
//        @AffectsHighlighting
//        var tool by enum(ExternalLinter.DEFAULT)

        @AffectsHighlighting
        var additionalArguments by property("") { it.isEmpty() }

//        @AffectsHighlighting
//        var channel by enum(RustChannel.DEFAULT)
        @AffectsHighlighting
        var envs by map<String, String>()
        @AffectsHighlighting
        var runOnTheFly by property(false)

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
