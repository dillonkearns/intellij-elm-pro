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
import com.intellij.psi.PsiManager
import com.intellij.util.ThreeState
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.xmlb.annotations.Transient
//import org.elm.ide.settings.ElmProjectConfigurable
import org.elm.ide.settings.ELmProjectSettingsService.ELmProjectSettings
//import org.elm.ide.toolchain.ExternalLinter
//import org.elm.ide.toolchain.ElmToolchainBase
//import org.elm.ide.toolchain.ElmToolchainProvider
import org.elm.openapiext.isUnitTestMode
//import org.elm.openapiext.showSettingsDialog
import java.nio.file.Paths

val Project.rustSettings: ELmProjectSettingsService
    get() = service<ELmProjectSettingsService>()

//val Project.toolchain: ElmToolchainBase?
//    get() {
//        val toolchain = rustSettings.state.toolchain
//        return when {
//            toolchain != null -> toolchain
////            isUnitTestMode -> ElmToolchainBase.suggest()
//            else -> null
//        }
//    }

private const val SERVICE_NAME: String = "ELmProjectSettings"

@State(name = SERVICE_NAME, storages = [
    Storage(StoragePathMacros.WORKSPACE_FILE),
    Storage("misc.xml", deprecated = true)
])
class ELmProjectSettingsService(
    project: Project
) : ElmProjectSettingsServiceBase<ELmProjectSettings>(project, ELmProjectSettings()) {
//    val toolchain: ElmToolchainBase? get() = state.toolchain
    val explicitPathToStdlib: String? get() = state.explicitPathToStdlib
    val autoShowErrorsInEditor: ThreeState get() = ThreeState.fromBoolean(state.autoShowErrorsInEditor)
    val autoUpdateEnabled: Boolean get() = state.autoUpdateEnabled
    val compileAllTargets: Boolean get() = state.compileAllTargets
    val useOffline: Boolean get() = state.useOffline
    val macroExpansionEngine: MacroExpansionEngine get() = state.macroExpansionEngine
    val doctestInjectionEnabled: Boolean get() = state.doctestInjectionEnabled

    class ELmProjectSettings : ElmProjectSettingsBase<ELmProjectSettings>() {
        @AffectsCargoMetadata
        var toolchainHomeDirectory by string()
        var autoShowErrorsInEditor by property(true)
        var autoUpdateEnabled by property(true)
        // Usually, we use `rustup` to find stdlib automatically,
        // but if one does not use rustup, it's possible to
        // provide path to stdlib explicitly.
        @AffectsCargoMetadata
        var explicitPathToStdlib by string()
        // BACKCOMPAT: 2023.1
//        var externalLinter by enum(ExternalLinter.DEFAULT)
        // BACKCOMPAT: 2023.1
        var runExternalLinterOnTheFly by property(false)
        // BACKCOMPAT: 2023.1
        var externalLinterArguments by property("") { it.isEmpty() }
        @AffectsHighlighting
        var compileAllTargets by property(true)
        var useOffline by property(false)
        var macroExpansionEngine by enum(defaultMacroExpansionEngine)
        @AffectsHighlighting
        var doctestInjectionEnabled by property(true)

//        @get:Transient
//        @set:Transient
//        var toolchain: ElmToolchainBase?
//            get() = toolchainHomeDirectory?.let { ElmToolchainProvider.getToolchain(Paths.get(it)) }
//            set(value) {
//                toolchainHomeDirectory = value?.location?.systemIndependentPath
//            }
//
        override fun copy(): ELmProjectSettings {
            val state = ELmProjectSettings()
            state.copyFrom(this)
            return state
        }
    }

    override fun loadState(state: ELmProjectSettings) {
        if (state.macroExpansionEngine == MacroExpansionEngine.OLD) {
            state.macroExpansionEngine = MacroExpansionEngine.NEW
        }
        super.loadState(state)
    }

    override fun notifySettingsChanged(event: SettingsChangedEventBase<ELmProjectSettings>) {
        super.notifySettingsChanged(event)

        if (event.isChanged(ELmProjectSettings::doctestInjectionEnabled)) {
            // flush injection cache
            PsiManager.getInstance(project).dropPsiCaches()
        }
    }

    override fun createSettingsChangedEvent(
        oldEvent: ELmProjectSettings,
        newEvent: ELmProjectSettings
    ): SettingsChangedEvent = SettingsChangedEvent(oldEvent, newEvent)

    class SettingsChangedEvent(
        oldState: ELmProjectSettings,
        newState: ELmProjectSettings
    ) : SettingsChangedEventBase<ELmProjectSettings>(oldState, newState)

    /*
     * Show a dialog for toolchain configuration
     */
//    fun configureToolchain() {
//        project.showSettingsDialog<ElmProjectConfigurable>()
//    }

    enum class MacroExpansionEngine {
        DISABLED,
        OLD, // `OLD` can't be selected by a user anymore, it exists for backcompat with saved user settings
        NEW
    }

    companion object {
        private val defaultMacroExpansionEngine: MacroExpansionEngine
            get() = MacroExpansionEngine.NEW
    }
}
