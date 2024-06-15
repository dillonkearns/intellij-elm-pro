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
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoChange
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.util.IncorrectOperationException
import org.elm.ElmBundle
import org.elm.lang.core.psi.ElmExposableTag
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.elements.*
import org.elm.openapiext.*
import org.elm.utils.toPsiDirectory
import org.elm.utils.toPsiFile
import org.elm.workspace.elmWorkspace
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.io.File
import java.nio.file.Path
import javax.swing.JComponent


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
        this.addItem(project.basePath!!.toPath().relativize(itemsToMove.first().elmFile.virtualFile.pathAsPath.parent))
    }

    private var newModuleUi: Boolean = true
    private val existingModules = JBTextField("").apply {
    }

    private val targetFileChooser = createTargetFileChooser(project)
    private val memberPanel: ElmMemberSelectionTable = createMemberSelectionPanel()
    private var searchForReferences: Boolean = true

    init {
        check(!isUnitTestMode)
        super.init()
        title = ElmBundle.message("dialog.title.move.module.items")
        validateButtons()
    }

    private fun createTargetFileChooser(project: Project): TextFieldWithBrowseButton {
        val fileChooser = TextFieldWithBrowseButton()
        fileChooser.addActionListener {
            val dialog = ElmFileChooserDialog(
                ElmBundle.message("text.choose.containing.file"),
                project,
                null,
                null,
                itemsToMove.first()
            )
            val targetFile1 = File(fileChooser.text);
            val targetPsiFile = targetFile1.toPsiFile(myProject)

            if (targetPsiFile != null) {
                if (targetPsiFile is ElmFile) {
                    dialog.select(targetPsiFile);
                } else {
                    val targetDir: PsiDirectory? = targetFile1.getParentFile().toPsiDirectory(myProject)
                    if (targetDir != null) {
                        dialog.selectDirectory(targetDir);
                    } else {
//                            dialog.selectDirectory(sourceDir);
                    }
                }
            } else {
//                    dialog.selectDirectory(sourceDir);
            }
            dialog.showDialog()
            val selectedFile: ElmFile? = if (dialog.isOK) {
                dialog.selected
            } else {
                null
            }
            if (selectedFile != null) {
                fileChooser.text = selectedFile.virtualFile.path;
            }
        }
        return fileChooser
    }

    private fun createMemberSelectionPanel(): ElmMemberSelectionTable {
        val topLevelItems = getTopLevelItems()
        val memberInfos: List<ElmMemberInfo> = topLevelItems.map { ElmMemberInfo(it, it in itemsToMove) }
        val selectionPanel = ElmMemberSelectionPanel(title, memberInfos, null)
        val memberTable = selectionPanel.getTable()
        val memberInfoModel = MemberInfoModelImpl()
        memberInfoModel.memberInfoChanged(MemberInfoChange(memberInfos))
        selectionPanel.getTable().memberInfoModel = memberInfoModel
        selectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel)
//        selectionPanel.getTable().addMemberInfoChangeListener { listener -> updateControls() }
//        cbApplyMPPDeclarationsMove.addChangeListener { e -> updateControls() }

        return memberTable

    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(ElmBundle.message("from")) {
                fullWidthCell(sourceFileField)
            }
            lateinit var rb: Cell<JBRadioButton>
            lateinit var rb2: Cell<JBRadioButton>
            buttonsGroup {
                    row {
                        resizableRow()
                        fullWidthCell(memberPanel)
                            .verticalAlign(VerticalAlign.FILL)
                    }
                row {
                    rb = radioButton("New Module", true).onChanged { newModuleUi = it.selected() }
                    fullWidthCell(targetFileChooser).focused().enabledIf(rb.selected)
                }
                group("To existing:") {
                    row {
                        rb2 = radioButton("Existing Module", false) //.onChanged { newModuleUi = !rb.selected() }
                    }
                    row(ElmBundle.message("source.directory")) {
                        fullWidthCell(sourceDirectory)
                    }.enabledIf(rb2.selected)
                    row(ElmBundle.message("module.name")) {
                        fullWidthCell(existingModules)
                    }.enabledIf(rb2.selected)


                }

            }.bind({ newModuleUi }, { newModuleUi = it })
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

        // TODO restore validation
        return /* sourceMod.virtualFile.pathAsPath!= existingModules.item.declaration.elmFile.virtualFile.pathAsPath &&  */ getSelectedItems().isNotEmpty()
    }

    private fun getSelectedItems(): Set<ElmExposableTag> {
        return memberPanel.selectedMemberInfos.mapNotNull { it.member as? ElmExposableTag }.toSet()
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
        val targetFilePath = selectedPath()
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

    private fun selectedPath(): Path = if (newModuleUi) {
        targetFileChooser.text.toPath()
    } else {
        val moduleFilePathPart = toElmFilePaths(existingModules.text!!)
        val resolvedModuleFilePath = project.basePath?.toPath()?.resolve(sourceDirectory.item)?.resolve(moduleFilePathPart)!!
        resolvedModuleFilePath
    }

    private fun toElmFilePaths(s: String): String {
        return s.replace(".", "/") + ".elm"
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

class MemberInfoModelImpl: MemberInfoModel<ElmNamedElement, ElmMemberInfo> {
    override fun memberInfoChanged(p0: MemberInfoChange<ElmNamedElement, ElmMemberInfo>) {
    }

    override fun isMemberEnabled(p0: ElmMemberInfo?): Boolean {
        return true
    }

    override fun isCheckedWhenDisabled(p0: ElmMemberInfo?): Boolean {
        return false
    }

    override fun isAbstractEnabled(p0: ElmMemberInfo?): Boolean {
        return false
    }

    override fun isAbstractWhenDisabled(p0: ElmMemberInfo?): Boolean {
        return false
    }

    override fun isFixedAbstract(p0: ElmMemberInfo?): Boolean {
        return false
    }

    override fun checkForProblems(p0: ElmMemberInfo): Int {
        return 0
    }

    override fun getTooltipText(p0: ElmMemberInfo?): String {
        return ""
    }
}

fun elmFilePathToModuleName(project: Project, elmFilePath: Path): String {
    // find which of the source-directories it belongs to, then get the file path relative to that
    val elmProject = project.elmWorkspace.allProjects.firstOrNull()
    val sourceDirs = elmProject?.sourceDirectories.orEmpty().map { SourceDirectory(project, it) }
    val sourceDir = sourceDirs.find { elmFilePath.startsWith(it.absolute()) }
    return sourceDir!!.absolute().relativize(elmFilePath).toString().replace(".elm", "").replace("/", ".")
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
            val moduleName = elmFilePathToModuleName(project, filePath)
            virtualFile.writeText("module $moduleName exposing (todoRemoveThis)\n\n")
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

data class SourceDirectory(val project: Project, val path: Path) {
    fun absolute(): Path {
        return project.basePath!!.toPath().resolve(path)
    }
}

class DeclarationWrapper(val declaration: ElmModuleDeclaration) {
    override fun toString(): String = declaration.name
}
