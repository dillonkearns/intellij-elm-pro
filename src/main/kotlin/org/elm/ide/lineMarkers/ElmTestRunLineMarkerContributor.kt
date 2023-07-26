/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.lineMarkers

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.elm.ide.test.core.LabelUtils
import org.elm.ide.test.core.LabelUtils.toPath
import org.elm.lang.core.psi.ElmPsiElement
import org.elm.lang.core.psi.elements.ElmFunctionCallExpr
import org.elm.lang.core.psi.elements.ElmStringConstantExpr
import org.elm.lang.core.psi.moduleName
import javax.swing.Icon

class ElmTestRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        when (element) {
            is ElmFunctionCallExpr -> {
                if (element.target.reference?.canonicalText == "describe") {
                    // TODO this assumes a single level of nesting, need to traverse up the tree to get the full path
                    val arg = element.arguments.first()
                    val describeName = if (arg is ElmStringConstantExpr) {
                        arg.textContent
                    } else {
                        null
                    }
                    if (describeName == null) {
                        return null
                    }
                    val testUrl =
                        LabelUtils.toLocationUrl(toPath((element as ElmPsiElement).moduleName, describeName), true)
                    val icon = getTestStateIcon(element, testUrl)
                    return Info(
                        icon,
                        { "Run" },
                        *ExecutorAction.getActions(1)
                    )
                } else if (element.target.reference?.canonicalText == "test") {
                    // TODO this assumes a single level of nesting, need to traverse up the tree to find any `describe`s that this test is contained within to get the full path
                    val arg = element.arguments.first()
                    val testName = if (arg is ElmStringConstantExpr) {
                        arg.textContent
                    } else {
                        null
                    }
                    val describePath = getDescribePath(element)
                    if (testName == null || describePath == null) {
                        return null
                    }
                    val fullPath =
                        arrayOf(
                            arrayOf((element as ElmPsiElement).moduleName),
                            describePath,
                            arrayOf(testName)
                        ).flatten()

                    val testUrl =
                        LabelUtils.toLocationUrl(toPath(*fullPath.toTypedArray()), false)
                    val icon = getTestStateIcon(element, testUrl)
                    return Info(
                        icon,
                        { "Run" },
                        *ExecutorAction.getActions(1)
                    )
                }
            }
        }
        return null
    }

    private fun getDescribePath(element: PsiElement): Array<String>? {
        return getDescribePathHelp(element.parent, arrayOf())
    }

    private fun getDescribePathHelp(element: PsiElement?, soFar: Array<String>?): Array<String>? {
        if (element == null || soFar == null) {
            return soFar
        } else {
            if (element is ElmFunctionCallExpr && element.target.reference?.canonicalText == "describe") {
                val firstArg = element.arguments.first()
                return if (firstArg is ElmStringConstantExpr) {
                    getDescribePathHelp(
                        element.parent,
                        arrayOf(firstArg.textContent) + soFar
                    )
                } else {
                    null
                }
            } else {
                return getDescribePathHelp(
                    element.parent,
                    soFar
                )

            }

        }
    }

    private fun getTestStateIcon(element: PsiElement, testUrl: String?): Icon? {
        val instance = TestStateStorage.getInstance(element.project)
        val state = instance.getState(testUrl)

        return when (state.let { it?.let { it1 -> TestIconMapper.getMagnitude(it1.magnitude) } }) {
            Magnitude.PASSED_INDEX,
            Magnitude.COMPLETE_INDEX ->
                AllIcons.RunConfigurations.TestState.Green2

            Magnitude.ERROR_INDEX,
            Magnitude.FAILED_INDEX ->
                AllIcons.RunConfigurations.TestState.Red2

            else -> AllIcons.RunConfigurations.TestState.Run
        }
    }
}
