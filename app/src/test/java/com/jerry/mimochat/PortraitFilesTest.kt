package com.jerry.mimochat

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PortraitFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun removesAbandonedImportsButKeepsSharedCharacterPortrait() {
        val directory = temporary.newFolder("portraits")
        val shared = File(directory, "shared.jpg").apply { writeText("portrait used by two characters") }
        val abandoned = File(directory, "abandoned.jpg").apply { writeText("discarded upload") }
        PortraitFiles.cleanup(directory, setOf(shared.name))
        assertTrue(shared.exists())
        assertFalse(abandoned.exists())
        PortraitFiles.cleanup(directory, emptySet())
        assertFalse(shared.exists())
    }

    @Test fun doesNotDeleteSiblingFilesOrDirectories() {
        val directory = temporary.newFolder("portraits")
        val sibling = temporary.newFile("conversation-state.json")
        val subdirectory = File(directory, "unrelated").apply { mkdir() }
        PortraitFiles.cleanup(directory, emptySet())
        assertTrue(sibling.exists())
        assertTrue(subdirectory.exists())
    }
}
