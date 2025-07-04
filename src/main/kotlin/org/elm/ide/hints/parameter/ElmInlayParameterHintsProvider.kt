/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.hints.parameter

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement


@Suppress("UnstableApiUsage")
class ElmInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getSupportedOptions(): List<Option> =
            listOf(ElmInlayParameterHints.enabledOption)

    override fun getDefaultBlackList(): Set<String> = emptySet()

    @OptIn(ExperimentalStdlibApi::class)
    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        if (ElmInlayParameterHints.enabled) {
            return ElmInlayParameterHints.provideHints(element)
        }
        return emptyList()
    }

    override fun getInlayPresentation(inlayText: String): String = inlayText
}
