/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.hints.parameter

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.elm.lang.core.psi.ElmNameDeclarationPatternTag
import org.elm.lang.core.psi.elements.ElmValueDeclaration
import org.elm.lang.core.psi.endOffset
import org.elm.lang.core.types.Ty
import org.elm.lang.core.types.TyUnknown
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.types.findInference
import org.elm.lang.core.types.findPipeTypes
import org.elm.lang.core.types.findTy
import org.elm.lang.core.types.renderedText

@Suppress("UnstableApiUsage")
object ElmInlayParameterHints {
    // BACKCOMPAT: 2020.1
    @Suppress("DEPRECATION")
    val enabledOption: Option = Option("SHOW_PARAMETER_HINT", "Show argument name hints", true)
    val enabled: Boolean get() = enabledOption.get()

    @ExperimentalStdlibApi
    fun provideHints(elem: PsiElement): List<InlayInfo> {
        return when (elem) {
            is ElmNameDeclarationPatternTag -> {
                listOfNotNull(typeHint(": ", elem.findTy(), elem.endOffset))
            }
            is ElmValueDeclaration -> {
                return if (elem.typeAnnotation == null) {
                    listOfNotNull(typeHint(" -- ", elem.findTy(), elem.eqElement?.endOffset!!))
                } else {
                    emptyList()
                }
            }
            is ElmBinOpExpr -> {
                if (elem.parts.none { it is ElmOperator && it.text == "|>" }) { return emptyList() }
                val pipeTypes = elem.findPipeTypes()

                return pipeTypes?.map { (expression, type) ->
                            InlayInfo(type.renderedText(), expression.endOffset)
                        }.orEmpty()

            }
            else -> {
                emptyList()
            }
        }
    }
}

fun typeHint(prefix: String, maybeType: Ty?, offset: Int): InlayInfo? {
    return maybeType?.let {
        if (it is TyUnknown) {
            null
        } else {
            InlayInfo(prefix + it.renderedText(), offset)
        }
    }
}
