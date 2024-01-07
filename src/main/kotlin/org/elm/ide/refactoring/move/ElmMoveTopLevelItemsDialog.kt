/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.elm.ide.refactoring.move

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.vfs.*
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.elm.ElmBundle
//import org.elm.ide.docs.signature
//import org.elm.lang.ElmConstants
import org.elm.lang.core.psi.*
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.stubs.index.ElmModulesIndex
//import org.elm.lang.core.psi.ext.ElmItemElement
//import org.elm.lang.core.psi.ext.ElmMod
import org.elm.openapiext.*
import org.elm.workspace.ElmPackageProject
import org.elm.workspace.elmWorkspace
//import org.elm.stdext.mapToSet
//import org.elm.stdext.toPath
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.nameWithoutExtension

class ElmMoveTopLevelItemsDialog(
    project: Project,
    private val itemsToMove: Set<ElmExposableTag>,
    private val sourceMod: ElmFile
) : RefactoringDialog(project, false) {

    @Nls
    private val sourceFilePath: String = sourceMod.containingFile.virtualFile.path
    private val sourceFileField: JBTextField = JBTextField(sourceFilePath).apply { isEnabled = false }
    @Nls

    private val sourceDirectory: ComboBox<Path> = ComboBox<Path>().apply {
        isEnabled = true
        val elmProject = project.elmWorkspace.allProjects.firstOrNull()
        elmProject?.sourceDirectories.orEmpty().forEach {
            this.addItem(it)
        }

    }
    private val existingModules = ComboBox<DeclarationWrapper>().apply {
        ElmModulesIndex.getAll(itemsToMove.first().elmFile).filter { it.elmProject !is ElmPackageProject }
            .filter {
                it.elmFile.virtualFile.pathAsPath.startsWith(it.elmFile.elmProject?.projectDirPath?.toRealPath()
                    ?.resolve(sourceDirectory.item) ?: return@filter false)
            }
            .sortedBy { it.moduleName  }
            .map { DeclarationWrapper(it) }
            .forEach(::addItem)
    }

    private val targetFileChooser: TextFieldWithBrowseButton = createTargetFileChooser(project)
    private val memberPanel: ElmMoveMemberSelectionPanel = createMemberSelectionPanel().apply {
        // Small hack to make Kotlin UI DSL 2 use proper minimal size
        // Actually, I don't know why it helps
        preferredSize = JBUI.size(0, 0)
    }

    private var searchForReferences: Boolean = true

    init {
        check(!isUnitTestMode)
        super.init()
        title = ElmBundle.message("dialog.title.move.module.items")
        validateButtons()
    }

    private fun createTargetFileChooser(project: Project): TextFieldWithBrowseButton {
        return pathToElmFileTextField(disposable, ElmBundle.message("dialog.title.choose.destination.file"), project, ::validateButtons)
            .also {
                it.text = sourceFilePath
                it.textField.caretPosition = sourceFilePath.removeSuffix(".elm").length
                it.textField.moveCaretPosition(sourceFilePath.lastIndexOf('/') + 1)
            }
    }

    private fun createMemberSelectionPanel(): ElmMoveMemberSelectionPanel {
        val topLevelItems = getTopLevelItems()

//        val nodesGroupedWithImpls = groupImplsByStructOrTrait(sourceMod, topLevelItems.toSet())
//            .map { ElmMoveItemAndImplsInfo(it.key, it.value) }
//        val itemsGroupedWithImpls = nodesGroupedWithImpls.flatMap { it.children }.map { it.member }
//
//        val nodesWithoutGrouping = topLevelItems.subtract(itemsGroupedWithImpls).map { ElmMoveMemberInfo(it) }
//        val nodesAll = nodesGroupedWithImpls + nodesWithoutGrouping
//
        val nodesAll = topLevelItems.map { ElmMoveItemAndImplsInfo(it) }
//        val nodesSelected = emptyList<ElmMoveNodeInfo>()

        val nodesSelected = nodesAll
//            .flatMap {
//                when (it) {
//                    is ElmMoveItemAndImplsInfo -> it.children
////                    is ElmMoveMemberInfo -> listOf(it)
//                    else -> error("unexpected node info type: $it")
//                }
//            }
            .filter { it.item in itemsToMove }
        return ElmMoveMemberSelectionPanel(project, ElmBundle.message("separator.items.to.move"), nodesAll, nodesSelected)
            .also { it.tree.setInclusionListener { validateButtons() } }
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(ElmBundle.message("from")) {
                fullWidthCell(sourceFileField)
            }
            row(ElmBundle.message("source.directory")) {
                fullWidthCell(sourceDirectory)
            }
            row(ElmBundle.message("source.directory")) {
                fullWidthCell(existingModules)
            }
            row(ElmBundle.message("to")) {
                fullWidthCell(targetFileChooser).focused()
            }
            row {
                resizableRow()
                fullWidthCell(memberPanel)
                    .verticalAlign(VerticalAlign.FILL)
            }
            row {
                checkBox(RefactoringBundle.message("search.for.references"))
                    .bindSelected(::searchForReferences)
            }
        }.also { it.preferredSize = Dimension(600, 400) }
    }

    private fun getTopLevelItems(): List<ElmExposableTag> {
        return sourceMod.children.mapNotNull {
            when (it) {
                is ElmValueDeclaration -> it.functionDeclarationLeft
                is ElmTypeDeclaration -> it
                is ElmTypeAliasDeclaration -> it
                is ElmPortAnnotation -> it
                else -> null
            }
        }.filter {
            ElmMoveTopLevelItemsHandler.canMoveElement(it)
        }
    }

    override fun areButtonsValid(): Boolean {
        // `memberPanel` is initialized after `createTargetFileChooser`,
        // which triggers `areButtonsValid` check
        @Suppress("SENSELESS_COMPARISON")
        if (memberPanel == null) return false

        return sourceMod.virtualFile.pathAsPath != existingModules.item.declaration.elmFile.virtualFile.pathAsPath && getSelectedItems().isNotEmpty()
    }

    // TODO
    private fun getSelectedItems(): Set<ElmExposableTag> {
        return memberPanel.tree.includedSet.map { ( it as ElmMoveItemAndImplsInfo).item }.toSet()
    }

    override fun doAction() {
        // we want that file creation is undo together with actual move
        CommandProcessor.getInstance().executeCommand(
            project,
            { doActionUndoCommand() },
            RefactoringBundle.message("move.title"),
            null
        )
    }

    private fun doActionUndoCommand() {
        val itemsToMove = getSelectedItems()
        val targetFilePath = existingModules.item.declaration.elmFile.virtualFile.path.toPath()
        val targetMod = getOrCreateTargetMod(targetFilePath, project, sourceMod) ?: return
        try {
            val processor = ElmMoveTopLevelItemsProcessor(project, itemsToMove, targetMod, searchForReferences)
            invokeRefactoring(processor)
            // TODO not sure why this is needed, maybe a breaking API change broke automatically closing the window on successful action
            this.close(0)
        } catch (e: Exception) {
            if (e !is IncorrectOperationException) {
                Logger.getInstance(ElmMoveTopLevelItemsDialog::class.java).error(e)
            }
            project.showErrorMessage(e.message)
        }
    }

    companion object {
        fun getOrCreateTargetMod(targetFilePath: Path, project: Project, crateRoot: ElmFile?): ElmFile? {
            val targetFile = LocalFileSystem.getInstance().findFileByNioFile(targetFilePath)
            return if (targetFile != null) {
                targetFile.toPsiFile(project) as? ElmFile
                    ?: run {
                        project.showErrorMessage(ElmBundle.message("dialog.message.target.file.must.be.elm.file"))
                        null
                    }
            } else {
                try {
                    createNewElmFile(targetFilePath, project, crateRoot, this)
                        ?: run {
                            project.showErrorMessage(ElmBundle.message("dialog.message.can.t.create.new.elm.file.or.attach.it.to.module.tree"))
                            null
                        }
                } catch (e: Exception) {
                    project.showErrorMessage(ElmBundle.message("dialog.message.error.during.creating.new.elm.file", e.message?:""))
                    null
                }
            }
        }

        private fun Project.showErrorMessage(@DialogMessage message: String?) {
            val title = RefactoringBundle.message("error.title")
            CommonRefactoringUtil.showErrorMessage(title, message, null, this)
        }
    }
}

//class ElmMoveMemberInfo(val member: ElmExposableTag) : ElmMoveNodeInfo {
//    override fun render(renderer: ColoredTreeCellRenderer) {
//        val description = if (member is ElmModItem) {
//            ElmBundle.message("mod.0", member.modName?:"")
//        } else {
//            val descriptionHTML = buildString { member.signature(this) }
//            val description = StringEscapeUtils.unescapeHtml(StringUtil.removeHtmlTags(descriptionHTML))
//            description.replace("(?U)\\s+".toRegex(), " ")
//        }
//        renderer.append(description, SimpleTextAttributes.REGULAR_ATTRIBUTES)
//    }
//
//    override val icon: Icon = member.getIcon(0)
//}
//
class ElmMoveItemAndImplsInfo(
    val item: ElmExposableTag, // struct or trait
) : ElmMoveNodeInfo {

    @Suppress("DialogTitleCapitalization")
    override fun render(renderer: ColoredTreeCellRenderer) {
        val name = item.name
        val keyword = when (item) {
            is ElmFunctionDeclarationLeft -> ElmBundle.message("function")
            is ElmTypeDeclaration -> ElmBundle.message("custom-type")
            is ElmTypeAliasDeclaration -> ElmBundle.message("type-alias")
            is ElmPortAnnotation -> ElmBundle.message("port")
            else -> return
        }
        renderer.append("$keyword ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        renderer.append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

//    override val children: List<ElmMoveMemberInfo> =
//        listOf(ElmMoveMemberInfo(item)) + impls.map { ElmMoveMemberInfo(it) }
}

val MOVE_TARGET_MOD_KEY: Key<ElmFile> = Key("ELM_MOVE_TARGET_MOD_KEY")
val MOVE_TARGET_FILE_PATH_KEY: Key<Path> = Key("ELM_MOVE_TARGET_FILE_PATH_KEY")
//
///** Creates new elm file and attaches it to parent mod */
private fun createNewElmFile(filePath: Path, project: Project, crateRoot: ElmFile?, requestor: Any?): ElmFile? {
    return project.runWriteCommandAction() {
        val fileSystem = (crateRoot as? ElmFile)?.virtualFile?.fileSystem ?: LocalFileSystem.getInstance()
        createNewFile(filePath, fileSystem, requestor) { virtualFile ->
            virtualFile.writeText("module ${filePath.nameWithoutExtension} exposing (todoRemoveThis)\n\n")
            val file = (virtualFile.toPsiFile(project) as? ElmFile) ?: return@createNewFile null

//            if (!attachFileToParentMod(file, project, crateRoot)) return@createNewFile null
            file
        }
    }
}
//
///** Finds parent mod of [file] and adds mod declaration to it */
//private fun attachFileToParentMod(file: ElmFile, project: Project, crateRoot: ElmMod?): Boolean {
//    if (file.isCrateRoot) return true
//    val (parentModOwningDirectory, modName) = if (file.name == ElmConstants.MOD_RS_FILE) {
//        file.parent?.parent to file.parent?.name
//    } else {
//        file.parent to FileUtil.getNameWithoutExtension(file.name)
//    }
//    val parentMod = parentModOwningDirectory?.getOwningMod(crateRoot)
//    if (parentMod == null || modName == null) return false
//    val psiFactory = ElmPsiFactory(project)
//    parentMod.insertModDecl(psiFactory, psiFactory.createModDeclItem(modName))
//    return true
//}

/**
 * Creates new file (along with parent directories).
 * Then computes [action] on created [VirtualFile].
 * If [action] returns `null`, then rollbacks any changes, that is deletes created file and directories.
 */
private fun <T> createNewFile(
    filePath: Path,
    // needed for correct work in tests
    fileSystem: VirtualFileSystem,
    requestor: Any?,
    action: (VirtualFile) -> T?
): T? {
    val directoryPath = filePath.parent
    val directoriesToCreate = generateSequence(directoryPath) { it.parent }
        .takeWhile { fileSystem.findFileByPath(it.toString()) == null }
        .toList()

    val parentDirectory = VfsUtil.createDirectoryIfMissing(fileSystem, directoryPath.toString()) ?: return null
    val file = parentDirectory.createChildData(requestor, filePath.fileName.toString())
    action(file)?.let { return it }

    // else we need to delete created file and directories
    file.delete(null)
    for (directory in directoriesToCreate) {
        fileSystem.findFileByPath(directory.toString())?.delete(requestor)
    }
    return null
}

class DeclarationWrapper(public val declaration: ElmModuleDeclaration) {
    override fun toString(): String = declaration.name
}