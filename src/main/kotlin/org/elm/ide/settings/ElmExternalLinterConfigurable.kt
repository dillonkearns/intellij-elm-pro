/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.elm.ElmBundle

class ElmExternalLinterConfigurable(project: Project) : ElmConfigurableBase(project, ElmBundle.message("settings.rust.external.linters.name")) {


    override fun createPanel(): DialogPanel = panel {
        val settings = project.experimentalFlags
        val state = settings.state.copy()

        row {
            checkBox(ElmBundle.message("settings.elm.feature.elm-review-on-the-fly.label"))
                .comment(ElmBundle.message("settings.elm.feature.elm-review-on-the-fly.comment"))
                .bindSelected(state::enableElmReviewOnTheFly)
        }

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
                it.enableElmReviewOnTheFly = state.enableElmReviewOnTheFly
            }
        }
    }
}
