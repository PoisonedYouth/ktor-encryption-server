package com.poisonedyouth.application

import java.nio.file.Files
import java.nio.file.Path


fun deleteDirectoryRecursively(path: Path) {
    if (Files.exists(path)) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.delete(it) }
    }
}