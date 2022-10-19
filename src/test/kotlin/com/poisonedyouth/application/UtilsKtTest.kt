package com.poisonedyouth.application

import kotlin.io.path.deleteIfExists
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class UtilsKtTest {

    @Test
    fun `deleteDirectoryRecursively is working for not existing directory`() {
        // given
        val notExistingDirectory = Paths.get("not existing")

        // when
        assertThatNoException().isThrownBy { deleteDirectoryRecursively(notExistingDirectory) }
    }

    @Test
    fun `deleteDirectoryRecursively is working for file`() {
        // given
        val file = Files.createTempFile("test", "txt")

        // when
        assertThatNoException().isThrownBy { deleteDirectoryRecursively(file) }

        file.deleteIfExists()
    }

    @Test
    fun `deleteDirectoryRecursively is working for empty directory`() {
        // given
        val emptyDirectory = Files.createTempDirectory("test")

        // when
        deleteDirectoryRecursively(emptyDirectory)

        // then
        assertThat(emptyDirectory).doesNotExist()
    }

    @Test
    fun `deleteDirectoryRecursively is working for directory with files`() {
        // given
        val emptyDirectory = Files.createTempDirectory("test")
        Files.createFile(emptyDirectory.resolve("file1"))
        Files.createFile(emptyDirectory.resolve("file2"))

        // when
        deleteDirectoryRecursively(emptyDirectory)

        // then
        assertThat(emptyDirectory).doesNotExist()
    }
}