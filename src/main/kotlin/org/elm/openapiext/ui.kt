/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 *
 * Originally from intellij-rust
 */

package org.elm.openapiext

import com.intellij.ui.dsl.builder.Cell
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.Alarm
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import kotlin.reflect.KProperty
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.elm.lang.core.ElmFileType
import javax.swing.JTextField


class UiDebouncer(
        private val parentDisposable: Disposable,
        private val delayMillis: Int = 200
) {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    /**
     * @param onUiThread: callback to be executed in EDT with **any** modality state.
     * Use it only for UI updates
     */
    fun <T> run(onPooledThread: () -> T, onUiThread: (T) -> Unit) {
        if (Disposer.isDisposed(parentDisposable)) return
        alarm.cancelAllRequests()
        alarm.addRequest({
            val r = onPooledThread()
            ApplicationManager.getApplication().invokeLater({
                if (!Disposer.isDisposed(parentDisposable)) {
                    onUiThread(r)
                }
            }, ModalityState.any())
        }, delayMillis)
    }
}


fun fileSystemPathTextField(
        disposable: Disposable,
        title: String,
        fileDescriptor: FileChooserDescriptor,
        onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton {

    val component = TextFieldWithBrowseButton(null, disposable)
    component.addBrowseFolderListener(title, null, null, fileDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
    component.childComponent.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            onTextChanged()
        }
    })

    return component
}

class CheckboxDelegate(private val checkbox: JBCheckBox) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return checkbox.isSelected
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        checkbox.isSelected = value
    }
}

fun <T : JComponent> Row.fullWidthCell(component: T): Cell<T> {
    return cell(component)
        .horizontalAlign(HorizontalAlign.FILL)
}

fun pathToElmFileTextField(
    disposable: Disposable,
    @NlsContexts.DialogTitle title: String,
    project: Project,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton =
    pathTextField(
        FileChooserDescriptorFactory
            .createSingleFileDescriptor(ElmFileType)
            .withRoots(project.guessProjectDir()),
        disposable,
        title,
        onTextChanged
    )

fun pathTextField(
    fileChooserDescriptor: FileChooserDescriptor,
    disposable: Disposable,
    @NlsContexts.DialogTitle title: String,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton {
    val component = TextFieldWithBrowseButton(null, disposable)
    component.addBrowseFolderListener(
        title, null, null,
        fileChooserDescriptor,
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    )
    component.childComponent.addTextChangeListener { onTextChanged() }
    return component
}

fun JTextField.addTextChangeListener(listener: (DocumentEvent) -> Unit) {
    document.addDocumentListener(
        object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                listener(e)
            }
        }
    )
}
