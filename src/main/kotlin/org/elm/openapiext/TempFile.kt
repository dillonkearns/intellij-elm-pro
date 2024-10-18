package org.elm.openapiext

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePointerCapableFileSystem
import com.intellij.openapi.vfs.ex.temp.TempFileSystemMarker
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.LocalTimeCounter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path


/* Copied from https://github.com/JetBrains/intellij-community/blob/aff0447560b2d514c50008866bfe76482344702e/platform/platform-impl/src/com/intellij/openapi/vfs/ex/temp/TempFileSystem.java */



class TempFileSystem : LocalFileSystemBase(), VirtualFilePointerCapableFileSystem, TempFileSystemMarker {
    private val myRoot: FSItem = FSDir()

    override fun extractRootPath(normalizedPath: String): String {
        return "/"
    }

    override fun getNioPath(file: VirtualFile): Path? {
        return null
    }

    private fun convert(file: VirtualFile): FSItem? {
        val parentFile = file.parent ?: return myRoot
        var item = file.getUserData(FS_ITEM_KEY)
        if (item == null) {
            val parentItem = convert(parentFile)
            if (parentItem == null || !parentItem.isDirectory) {
                return null
            }
            item = parentItem.findChild(file.name)
            registerFSItem(file, item)
        }
        return item
    }

    @Throws(IOException::class)
    private fun convertDirectory(dir: VirtualFile): FSDir {
        val fsItem = convertAndCheck(dir)
        if (!fsItem.isDirectory) {
            throw IOException("Not a directory: " + dir.path)
        }
        return fsItem as FSDir
    }

    private fun convertAndCheck(file: VirtualFile): FSItem {
        val fsItem = convert(file)
            ?: //MAYBE RC: NoSuchFileException seems to fit better:
            // 1. it is an IOException -- no need to catch out-of-nothing IllegalStateException (see LightPlatformTestCase.tearDownSourceRoot())
            // 2. it is thrown by LocalFileSystemBase in case there is no such file -- more unified
            throw IllegalStateException("Does not exist: " + file.path)
        return fsItem
    }

    @Throws(IOException::class)
    override fun createChildDirectory(requestor: Any?, parent: VirtualFile, name: String): VirtualFile {
        val fsDir = convertDirectory(parent)
        val existing = fsDir.findChild(name)
        if (existing == null) {
            fsDir.addChild(name, FSDir())
        } else if (!existing.isDirectory) {
            throw IOException("File " + name + " already exists in " + parent.path)
        }
        return FakeVirtualFile(parent, name)
    }

    @Throws(IOException::class)
    override fun createChildFile(requestor: Any?, parent: VirtualFile, name: String): VirtualFile {
        val fsDir = convertDirectory(parent)
        if (fsDir.findChild(name) != null) throw IOException("File " + name + " already exists in " + parent.path)
        fsDir.addChild(name, FSFile())
        return FakeVirtualFile(parent, name)
    }

    @Throws(IOException::class)
    override fun copyFile(
        requestor: Any,
        file: VirtualFile,
        newParent: VirtualFile,
        copyName: String
    ): VirtualFile {
        return VfsUtilCore.copyFile(requestor, file, newParent, copyName)
    }

    @Throws(IOException::class)
    override fun deleteFile(requestor: Any, file: VirtualFile) {
        val parent = convertAndCheckParent(file)
        parent.removeChild(file.name, file.parent)
        clearFsItemCache(file)
    }

    private fun convertAndCheckParent(file: VirtualFile): FSDir {
        return convertAndCheck(file.parent) as FSDir
    }

    @Throws(IOException::class)
    override fun moveFile(requestor: Any, file: VirtualFile, newParent: VirtualFile) {
        val fsItem = convertAndCheck(file)
        val newParentItem = convertAndCheck(newParent)
        val oldParentItem = convertAndCheckParent(file)
        if (!newParentItem.isDirectory) throw IOException("Target is not a directory: " + file.path)
        val newDir = newParentItem as FSDir
        val name = file.name
        if (newDir.findChild(name) != null) throw IOException("Directory already contains a file named $name")
        oldParentItem.removeChild(name, file.parent)
        newDir.addChild(name, fsItem)
        clearFsItemCache(file)
    }

    @Throws(IOException::class)
    override fun renameFile(requestor: Any, file: VirtualFile, newName: String) {
        setName(file, newName)
    }

    override fun getProtocol(): String {
        return TEMP_PROTOCOL
    }

    override fun exists(fileOrDirectory: VirtualFile): Boolean {
        return convert(fileOrDirectory) != null
    }

    override fun list(file: VirtualFile): Array<String> {
        val fsItem = convertAndCheck(file)
        return fsItem.list()
    }

    override fun getCanonicallyCasedName(file: VirtualFile): String {
        return file.name
    }

    override fun isDirectory(file: VirtualFile): Boolean {
        return convert(file) is FSDir
    }

    override fun getTimeStamp(file: VirtualFile): Long {
        val fsItem = convertAndCheck(file)
        return fsItem.myTimestamp
    }

    override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
        val fsItem = convertAndCheck(file)
        fsItem.myTimestamp = if (timeStamp > 0) timeStamp else LocalTimeCounter.currentTime()
    }

    override fun isWritable(file: VirtualFile): Boolean {
        val fsItem = convertAndCheck(file)
        return fsItem.myWritable
    }

    override fun setWritable(file: VirtualFile, writableFlag: Boolean) {
        val fsItem = convertAndCheck(file)
        fsItem.myWritable = writableFlag
    }

    @Throws(IOException::class)
    override fun contentsToByteArray(file: VirtualFile): ByteArray {
        val fsItem = convertAndCheck(file) as? FSFile ?: throw IOException("Not a file: " + file.path)
        return fsItem.myContent
    }

    @Throws(IOException::class)
    override fun getInputStream(file: VirtualFile): InputStream {
        return BufferExposingByteArrayInputStream(contentsToByteArray(file))
    }

    @Throws(IOException::class)
    override fun getOutputStream(file: VirtualFile, requestor: Any, modStamp: Long, timeStamp: Long): OutputStream {
        return object : ByteArrayOutputStream() {
            @Throws(IOException::class)
            override fun close() {
                super.close()
                val fsItem = convertAndCheck(file) as? FSFile ?: throw IOException("Not a file: " + file.path)
                fsItem.myContent = toByteArray()
                setTimeStamp(file, modStamp)
            }
        }
    }

    override fun getLength(file: VirtualFile): Long {
        return try {
            contentsToByteArray(file).size.toLong()
        } catch (e: IOException) {
            0
        }
    }

    private abstract class FSItem {
        var myTimestamp: Long = LocalTimeCounter.currentTime()
        var myWritable: Boolean = true

        open val isDirectory: Boolean
            get() = false

        open fun findChild(name: String): FSItem? {
            return null
        }

        open fun list(): Array<String> {
            return ArrayUtil.EMPTY_STRING_ARRAY
        }
    }

    private class FSDir : FSItem() {
        val myChildren: MutableMap<String, FSItem> = LinkedHashMap()

        override fun findChild(name: String): FSItem? {
            return myChildren[name]
        }

        override val isDirectory: Boolean
            get() = true

        fun addChild(name: String, item: FSItem) {
            myChildren[name] = item
        }

        fun removeChild(name: String, parent: VirtualFile?) {
            if (name == "src" && parent == null) {
                throw RuntimeException("removing 'temp:///src' directory")
            }
            myChildren.remove(name)
        }
    }

    private class FSFile : FSItem() {
        var myContent: ByteArray = ArrayUtil.EMPTY_BYTE_ARRAY
    }

    private fun setName(file: VirtualFile, name: String) {
        val parent = convertAndCheckParent(file)
        val fsItem = convertAndCheck(file)
        parent.myChildren.remove(file.name)
        parent.myChildren[name] = fsItem
        clearFsItemCache(file.parent)
        clearFsItemCache(file)
    }

    override fun getAttributes(file: VirtualFile): FileAttributes? {
        val item = convert(file) ?: return null
        val length = (if (item is FSFile) item.myContent.size else 0).toLong()
        // let's make TempFileSystem case-sensitive
        return FileAttributes(
            item.isDirectory,
            false,
            false,
            false,
            length,
            item.myTimestamp,
            item.myWritable,
            FileAttributes.CaseSensitivity.SENSITIVE
        )
    }

    override fun replaceWatchedRoots(
        watchRequests: Collection<WatchRequest>,
        recursiveRoots: Collection<String>?,
        flatRoots: Collection<String>?
    ): Set<WatchRequest> {
        throw IncorrectOperationException()
    }

    override fun normalize(path: String): String {
        return path
    }

    companion object {
        private const val TEMP_PROTOCOL = "temp"

        val instance: TempFileSystem
            get() = VirtualFileManager.getInstance().getFileSystem(TEMP_PROTOCOL) as TempFileSystem

        private val FS_ITEM_KEY: Key<FSItem> = Key.create("FS_ITEM_KEY")

        private fun registerFSItem(parent: VirtualFile, item: FSItem?) {
            if (parent !is StubVirtualFile) {
                parent.putUserData(FS_ITEM_KEY, item)
            }
        }

        private fun clearFsItemCache(file: VirtualFile) {
            registerFSItem(file, null)
        }
    }
}
