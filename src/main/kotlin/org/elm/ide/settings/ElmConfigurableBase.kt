/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import com.intellij.util.PlatformUtils

@Suppress("UnstableApiUsage")
abstract class ElmConfigurableBase(
    protected val project: Project,
    @ConfigurableName displayName: String
) : BoundConfigurable(displayName) {
}
