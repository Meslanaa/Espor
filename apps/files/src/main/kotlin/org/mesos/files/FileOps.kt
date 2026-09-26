package org.mesos.files

import java.io.File
import java.io.IOException

enum class FileError { NAME_EMPTY, NAME_INVALID, EXISTS, INTO_ITSELF, FAILED }

class FileOpException(val error: FileError, message: String? = null) : IOException(message ?: error.name)

/** File operations for MesOS Files. Plain java.io; call from a background thread. */
object FileOps {

    private const val MAX_NAME_BYTES = 255

    fun validateName(name: String): FileError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> FileError.NAME_EMPTY
            trimmed == "." || trimmed == ".." -> FileError.NAME_INVALID
            trimmed.any { it == '/' || it == '\u0000' } -> FileError.NAME_INVALID
            trimmed.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> FileError.NAME_INVALID
            else -> null
        }
    }

    /** Folders first, then files; each group by name, case-insensitively. */
    fun sort(files: List<File>): List<File> =
        files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** [name] inside [dir], or "name (1).ext", "name (2).ext", … when it already exists. */
    fun uniqueTarget(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var n = 1
        while (true) {
            val candidate = File(dir, "$base ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** True when [file] is [dir] itself or somewhere below it. */
    fun isInside(file: File, dir: File): Boolean {
        val child = file.canonicalFile
        val parent = dir.canonicalFile
        return child == parent || child.path.startsWith(parent.path + File.separator)
    }

    fun createFolder(parent: File, name: String): File {
        validateName(name)?.let { throw FileOpException(it) }
        val folder = File(parent, name.trim())
        if (folder.exists()) throw FileOpException(FileError.EXISTS)
        if (!folder.mkdir()) throw FileOpException(FileError.FAILED, "mkdir ${folder.path}")
        return folder
    }

    fun rename(file: File, newName: String): File {
        validateName(newName)?.let { throw FileOpException(it) }
        val target = File(file.parentFile, newName.trim())
        if (target == file) return file
        if (target.exists()) throw FileOpException(FileError.EXISTS)
        if (!file.renameTo(target)) throw FileOpException(FileError.FAILED, "rename ${file.path}")
        return target
    }

    /** Copies [source] (file or folder) into [targetDir]; returns the created copy. */
    fun copy(source: File, targetDir: File): File {
        if (source.isDirectory && isInside(targetDir, source)) throw FileOpException(FileError.INTO_ITSELF)
        val target = uniqueTarget(targetDir, source.name)
        try {
            if (!source.copyRecursively(target, overwrite = false)) throw FileOpException(FileError.FAILED)
        } catch (e: FileOpException) {
            throw e
        } catch (e: Exception) {
            target.deleteRecursively()
            throw FileOpException(FileError.FAILED, e.message)
        }
        return target
    }

    /** Moves [source] into [targetDir]; returns its new location. */
    fun move(source: File, targetDir: File): File {
        if (source.parentFile?.canonicalFile == targetDir.canonicalFile) return source
        if (source.isDirectory && isInside(targetDir, source)) throw FileOpException(FileError.INTO_ITSELF)
        val target = uniqueTarget(targetDir, source.name)
        if (source.renameTo(target)) return target
        // Different volume: copy, then remove the original only after the copy succeeded.
        val copied = copy(source, targetDir)
        if (!source.deleteRecursively()) throw FileOpException(FileError.FAILED, "delete ${source.path}")
        return copied
    }

    fun delete(file: File) {
        if (!file.deleteRecursively()) throw FileOpException(FileError.FAILED, "delete ${file.path}")
    }

    /** All regular files at or below [file], for media rescans (bounded). */
    fun filesBelow(file: File, limit: Int = 2000): List<File> =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.take(limit).toList() else listOf(file)
}
