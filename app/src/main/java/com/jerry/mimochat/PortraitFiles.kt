package com.jerry.mimochat

import java.io.File

object PortraitFiles {
    fun cleanup(directory: File, referenced: Set<String>) {
        directory.listFiles()?.filter { it.isFile && it.name !in referenced }?.forEach { it.delete() }
    }
}
