/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.settings

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.builder.*
import org.elm.ElmBundle
import org.elm.openapiext.fullWidthCell
import javax.swing.JLabel

class ElmExternalLinterConfigurable(project: Project) : ElmConfigurableBase(project, ElmBundle.message("settings.rust.external.linters.name")) {


    override fun createPanel(): DialogPanel = panel {
        val settings = project.experimentalFlags
        val state = settings.state.copy()

        row {
            checkBox(ElmBundle.message("settings.elm.feature.unstable.add-debug.label"))
                .comment(ElmBundle.message("settings.elm.feature.unstable.add-debug.comment"))
                .bindSelected(state::enableDebugIntention)
        }

        row {
            checkBox(ElmBundle.message("settings.elm.feature.unstable.extract-variable.label"))
                .comment(ElmBundle.message("settings.elm.feature.unstable.extract-variable.comment"))
                .bindSelected(state::enableExtractVariable)
        }

        onApply {
            settings.modify {
                it.additionalArguments = state.additionalArguments
                it.enableDebugIntention = state.enableDebugIntention
                it.enableExtractVariable = state.enableExtractVariable
            }
        }
    }
}
