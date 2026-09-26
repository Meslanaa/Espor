package org.mesos.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOpsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, text: String = "x"): File =
        File(tmp.root, path).apply { parentFile?.mkdirs(); writeText(text) }

    private fun dir(path: String): File = File(tmp.root, path).apply { mkdirs() }

    @Test
    fun validatesNames() {
        assertNull(FileOps.validateName("Holiday photos"))
        assertEquals(FileError.NAME_EMPTY, FileOps.validateName("  "))
        assertEquals(FileError.NAME_INVALID, FileOps.validateName(".."))
        assertEquals(FileError.NAME_INVALID, FileOps.validateName("a/b"))
        assertEquals(FileError.NAME_INVALID, FileOps.validateName("x".repeat(256)))
    }

    @Test
    fun sortsFoldersFirstCaseInsensitively() {
        val names = FileOps.sort(listOf(file("b.txt"), dir("Zeta"), file("A.txt"), dir("alpha"))).map { it.name }
        assertEquals(listOf("alpha", "Zeta", "A.txt", "b.txt"), names)
    }

    @Test
    fun uniqueTargetAddsCounterBeforeExtension() {
        file("report.pdf")
        file("report (1).pdf")
        assertEquals("report (2).pdf", FileOps.uniqueTarget(tmp.root, "report.pdf").name)
        assertEquals("notes", FileOps.uniqueTarget(tmp.root, "notes").name)
        dir("Music")
        assertEquals("Music (1)", FileOps.uniqueTarget(tmp.root, "Music").name)
    }

    @Test
    fun copiesFilesAndFoldersWithoutOverwriting() {
        val src = file("a/photo.jpg", "img")
        val dest = dir("b")
        assertEquals("img", FileOps.copy(src, dest).readText())
        assertEquals("photo (1).jpg", FileOps.copy(src, dest).name)

        file("tree/sub/deep.txt", "deep")
        val copied = FileOps.copy(File(tmp.root, "tree"), dest)
        assertEquals("deep", File(copied, "sub/deep.txt").readText())
        assertTrue(File(tmp.root, "tree/sub/deep.txt").exists())
    }

    @Test
    fun movesAndRefusesMovingFolderIntoItself() {
        val src = file("a/doc.txt", "doc")
        val moved = FileOps.move(src, dir("b"))
        assertFalse(src.exists())
        assertEquals("doc", moved.readText())
        assertEquals(moved, FileOps.move(moved, File(tmp.root, "b")))

        val folder = dir("outer")
        val inner = dir("outer/inner")
        val error = assertThrows(FileOpException::class.java) { FileOps.move(folder, inner) }
        assertEquals(FileError.INTO_ITSELF, error.error)
        assertThrows(FileOpException::class.java) { FileOps.copy(folder, folder) }
    }

    @Test
    fun renamesCreatesAndDeletes() {
        val f = file("old.txt")
        assertEquals("new.txt", FileOps.rename(f, "new.txt").name)
        file("taken.txt")
        assertEquals(FileError.EXISTS, assertThrows(FileOpException::class.java) {
            FileOps.rename(File(tmp.root, "new.txt"), "taken.txt")
        }.error)

        val folder = FileOps.createFolder(tmp.root, " Projects ")
        assertEquals("Projects", folder.name)
        assertEquals(FileError.EXISTS, assertThrows(FileOpException::class.java) {
            FileOps.createFolder(tmp.root, "Projects")
        }.error)

        file("Projects/a/b.txt")
        FileOps.delete(folder)
        assertFalse(folder.exists())
    }

    @Test
    fun isInsideUsesPathBoundaries() {
        val a = dir("music")
        assertTrue(FileOps.isInside(dir("music/rock"), a))
        assertTrue(FileOps.isInside(a, a))
        assertFalse(FileOps.isInside(dir("music2"), a))
    }
}
