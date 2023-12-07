package org.elm.ide.refactoring.introduceParameter

//import org.elm.ide.navigation.hidePopupIfDumbModeStarts
//import org.elm.ide.refactoring.MOCK
//import org.elm.lang.core.psi.RsFunction
//import org.elm.lang.core.psi.ext.title
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.editor.Editor
import org.elm.ElmBundle
import java.util.concurrent.atomic.AtomicReference

fun showEnclosingFunctionsChooser(editor: Editor,
//                                  methods: List<RsFunction>,
//                                  callback: (RsFunction) -> Unit
                                  )
{
//    if (isUnitTestMode && methods.size > 1) {
//        callback(MOCK!!.chooseMethod(methods))
//        return
//    }
    val highlighter = AtomicReference(ScopeHighlighter(editor))
    val title = ElmBundle.message("introduce.parameter.to.function")
//    val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(methods)
//        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
//        .setSelectedValue(methods.first(), true)
//        .setAccessibleName(title)
//        .setTitle(title)
//        .setMovable(false)
//        .setResizable(false)
//        .setRequestFocus(true)
//        .setItemChosenCallback { method ->
//            callback(method)
//        }
//        .addListener(object : JBPopupListener {
//            override fun onClosed(event: LightweightWindowEvent) {
//                highlighter.getAndSet(null).dropHighlight()
//            }
//        })
//        .setRenderer(object : DefaultListCellRenderer() {
//            override fun getListCellRendererComponent(list: JList<*>,
//                                                      value: Any?,
//                                                      index: Int,
//                                                      isSelected: Boolean,
//                                                      cellHasFocus: Boolean): Component {
//                val rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
//                text = (value as RsFunction).title
//                return rendererComponent
//            }
//        }).createPopup()
//    popup.showInBestPositionFor(editor)
//    val project = editor.project
//    if (project != null) {
//        hidePopupIfDumbModeStarts(popup, project)
//    }
}
