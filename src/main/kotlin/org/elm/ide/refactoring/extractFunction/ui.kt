package org.elm.ide.refactoring.extractFunction

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.util.childrenOfType
import com.intellij.refactoring.ui.MethodSignatureComponent
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.elm.ide.refactoring.isValidLowerIdentifier
import org.elm.ide.utils.findExpressionInRange
import org.elm.lang.core.ElmFileType
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.elements.ElmValueExpr
import org.elm.lang.core.resolve.scope.ExpressionScope
import org.elm.lang.core.resolve.scope.ModuleScope
import org.elm.lang.core.types.Ty
import org.elm.lang.core.types.findTy
import org.elm.lang.core.types.renderedText
import org.elm.openapiext.fullWidthCell
import org.elm.openapiext.isUnitTestMode
import org.jetbrains.annotations.TestOnly

private var MOCK: ExtractFunctionUi? = null

fun extractFunctionDialog(
    project: Project,
    config: ElmExtractFunctionConfig,
    callback: () -> Unit

) {
    val extractFunctionUi = if (isUnitTestMode) {
        MOCK ?: error("You should set mock ui via `withMockExtractFunctionUi`")
    } else {
        DialogExtractFunctionUi(project)
    }
    extractFunctionUi.extract(config, callback)
}

class ElmExtractFunctionConfig(var name: String, var visibilityLevelPublic: Boolean,
                               var parameters: List<Parameter>,
                               val selection: Pair<Int, Int>,
                               val expressionToExtract: ElmExpressionTag
) {
    val signature: String
        get() {
            // TODO handle parens if needed around function-type values in annotation?
            val signatureTypes: List<Ty?> = parameters.map { it.type } + listOf(expressionToExtract.findTy())
            val annotation = if (signatureTypes.all { it != null }) {
                "$name : " + signatureTypes.joinToString(" -> ") { it!!.renderedText().replace("→", "->") }
            } else { null }

            val parameterString = (listOf(name) + parameters.map { it.name } + listOf("=")).joinToString(" ")
            return "$annotation\n$parameterString"
        }
    companion object {
        fun createConfig(file: ElmFile, start: Int, end: Int): ElmExtractFunctionConfig {
            val expressionToExtract = findExpressionInRange(file, start, end)
            var relevantPatterns = expressionToExtract?.originalElement?.childrenOfType<ElmValueExpr>()?.toList().orEmpty()
            val self = expressionToExtract?.originalElement
            if (self is ElmValueExpr) {
                relevantPatterns = relevantPatterns.plus(self)
            }
            val localScopedValues = ExpressionScope(expressionToExtract!!).getVisibleValues().toSet().minus(
                ModuleScope.getVisibleValues(expressionToExtract.elmFile).all.toSet()
            )
            val parameters: List<Parameter> = relevantPatterns.flatMap { pattern ->
                val resolved = pattern.reference.resolve()
                if (localScopedValues.contains(resolved)) {
                    listOf(Parameter(resolved?.name!!, pattern.findTy()))
                } else {
                    emptyList()
                }
            }
            return ElmExtractFunctionConfig("", false, parameters, Pair(start, end), expressionToExtract)
        }
    }
}


@TestOnly
fun withMockExtractFunctionUi(mockUi: ExtractFunctionUi, action: () -> Unit) {
    MOCK = mockUi
    try {
        action()
    } finally {
        MOCK = null
    }
}

interface ExtractFunctionUi {
    fun extract(config: ElmExtractFunctionConfig, callback: () -> Unit)
}

private class DialogExtractFunctionUi(
    private val project: Project
) : ExtractFunctionUi {

    override fun extract(config: ElmExtractFunctionConfig, callback: () -> Unit) {
        val functionNameField = NameSuggestionsField(emptyArray(), project, ElmFileType)
        functionNameField.minimumSize = JBUI.size(300, 30)

        val visibilityBox = ComboBox<String>()
        with(visibilityBox) {
            addItem("Exposed")
            addItem("Not Exposed")
        }
        visibilityBox.selectedItem = "Not Exposed"
        val signatureComponent = ElmSignatureComponent(config.signature, project)
        signatureComponent.minimumSize = JBUI.size(300, 30)

        visibilityBox.addActionListener {
            updateConfig(config, functionNameField, visibilityBox)
            signatureComponent.setSignature(config.signature)
        }

        val parameterPanel = ExtractFunctionParameterTablePanel(::isValidElmVariableIdentifier, config) { signatureComponent.setSignature(config.signature)
        }

        val panel = panel {
            row("Name") { fullWidthCell(functionNameField) }
            row("Visibility") { cell(visibilityBox) }
            row("Parameters") { fullWidthCell(parameterPanel) }
            row("Signature") { fullWidthCell(signatureComponent) }
        }

        val extractDialog = dialog(
            "Extract Function",
            panel,
            resizable = true,
            focusedComponent = functionNameField.focusableComponent,
            okActionEnabled = false,
            project = project,
            parent = null,
            errorText = null,
            modality = DialogWrapper.IdeModalityType.IDE
        ) {
            updateConfig(config, functionNameField, visibilityBox)
            callback()
            emptyList()
        }

        functionNameField.addDataChangedListener {
            updateConfig(config, functionNameField, visibilityBox)
            signatureComponent.setSignature(config.signature)
            extractDialog.isOKActionEnabled = isValidElmVariableIdentifier(config.name)
        }
        extractDialog.show()
        // TODO implement extract?
    }

    private fun isValidElmVariableIdentifier(name: String): Boolean {
        return isValidLowerIdentifier(name)
    }

    private fun updateConfig(
        config: ElmExtractFunctionConfig,
        functionName: NameSuggestionsField,
        visibilityBox: ComboBox<String>
    ) {
        config.name = functionName.enteredName
        config.visibilityLevelPublic = visibilityBox.selectedItem == "Exposed"
    }
}

private class ElmSignatureComponent(
    signature: String,
    project: Project
) : MethodSignatureComponent(signature, project, ElmFileType) {
    private val myFileName = "dummy." + ElmFileType.defaultExtension

    override fun getFileName(): String = myFileName
}

class Parameter constructor(
    var name: String,
    val type: Ty? = null,
    private val isReference: Boolean = false,
    var isMutable: Boolean = false,
    private val requiresMut: Boolean = false,
    var isSelected: Boolean = true
) {
    /** Original name of the parameter (parameter renaming does not affect it) */
    private val originalName = name

    private val mutText: String
        get() = if (isMutable && (!isReference || requiresMut)) "mut " else ""
    private val referenceText: String
        get() = if (isReference) {
            if (isMutable) "&mut " else "&"
        } else {
            ""
        }
//    private val typeText: String = type?.renderInsertionSafe().orEmpty()

//    val originalParameterText: String
//        get() = if (type != null) "$mutText$originalName: $referenceText$typeText" else originalName

//    val parameterText: String
//        get() = if (type != null) "$mutText$name: $referenceText$typeText" else name
//
//    val argumentText: String
//        get() = "$referenceText$originalName"
//
    val isSelf: Boolean
        get() = type == null

//    companion object {
//        private fun direct(value: RsPatBinding, requiredBorrowing: Boolean, requiredMutableValue: Boolean): Parameter {
//            val reference = when {
//                requiredMutableValue -> requiredBorrowing
//                value.mutability.isMut -> true
//                requiredBorrowing -> true
//                else -> false
//            }
//            val mutable = when {
//                requiredMutableValue -> true
//                value.mutability.isMut -> true
//                else -> false
//            }
//            return Parameter(value.referenceName, value.type, reference, mutable, requiredMutableValue)
//        }
//
//        fun self(name: String): Parameter =
//            Parameter(name)
//
//        // TODO: Get rid of the heuristics and implement proper borrow analysis
//        fun build(
//            binding: RsPatBinding,
//            references: List<PsiReference>,
//            isUsedAfterEnd: Boolean,
//            implLookup: ImplLookup
//        ): Parameter {
//            val hasRefOperator = references.any {
//                val operatorType = (it.element.ancestorStrict<RsUnaryExpr>())?.operatorType
//                operatorType == UnaryOperator.REF || operatorType == UnaryOperator.REF_MUT
//            }
//            val requiredBorrowing = hasRefOperator ||
//                    (isUsedAfterEnd && binding.type !is TyReference && !implLookup.isCopy(binding.type).isTrue)
//
//            val requiredMutableValue = binding.mutability.isMut && references.any {
//                if (it.element.ancestorStrict<RsValueArgumentList>() == null) return@any false
//                val operatorType = it.element.ancestorStrict<RsUnaryExpr>()?.operatorType
//                operatorType == null || operatorType == UnaryOperator.REF_MUT
//            }
//
//            return direct(binding, requiredBorrowing, requiredMutableValue)
//        }
//    }
}
