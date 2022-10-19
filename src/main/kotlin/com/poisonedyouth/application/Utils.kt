package com.poisonedyouth.application

import java.nio.file.Files
import java.nio.file.Path


fun deleteDirectoryStream(path: Path) {
    Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach { Files.delete(it) }
}