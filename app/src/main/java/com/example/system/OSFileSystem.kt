package com.example.system

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

data class OSFile(
    val name: String,
    var content: String,
    val isDirectory: Boolean = false,
    val path: String
)

class OSFileSystem {
    val files: SnapshotStateList<OSFile> = mutableStateListOf()

    init {
        // Seed default directories
        createDirectory("/User")
        createDirectory("/User/Documents")
        createDirectory("/User/Pictures")
        createDirectory("/System")
        createDirectory("/System/bin")
        createDirectory("/System/etc")
        createDirectory("/Cloud")
        createDirectory("/Cloud/OneDrive")

        // Seed default files
        createFile("/User/Documents/about_hyper_os.txt", """
            =========================================
            HYPER OS v1.0 (CONCEPT BUILD)
            =========================================
            Created by: THE TUCCI CYBER NATION (TCN™)
            Developer: Nebula Dynamics Systems
            For: Professional Workspaces & Power Users
            
            FEATURES INSTALLED:
            - Hyper Explorer (Multi-tab file broker)
            - Hyper AI System Guardian Companion
            - Custom Color Palette Selector
            - Real-Time Thread Protection Dashboard
            - Fluent Acrylic UI with Glassmorphism
            
            Copyright © 2026 Nebula Dynamics. All rights reserved.
        """.trimIndent())

        createFile("/User/Documents/system_release.json", """
            {
              "os_name": "Hyper OS",
              "version": "1.0.0-ReleaseCandidate",
              "system_core": "NextGen-Microkernel-v4.1",
              "heap_limit": "2048MB",
              "graphics_driver": "TCN-Vulkan-API-v2.0",
              "ai_coprocessor": "HyperAI-Neural-Sync"
            }
        """.trimIndent())

        createFile("/Cloud/OneDrive/project_nebula.md", """
            # Project Nebula Roadmap
            
            1. Microkernel Integration
               - Optimize light kernel boot time (< 1.5 seconds)
               - Establish biometric lock gates
            
            2. Hyper AI Synchronizer
               - Setup adaptive resource allocation
               - Voice & Natural Language command terminal
            
            3. Global Cyber Network (TCN Grid)
               - Cloud-to-edge hybrid task scheduling
               - Real-time threat detection shield
        """.trimIndent())

        createFile("/System/etc/hosts", """
            127.0.0.1       localhost
            ::1             localhost
            10.0.0.1        gateway.hyper.os
            10.0.0.85       ai-core-cluster.tcn
        """.trimIndent())

        createFile("/System/bin/init.sh", """
            #!/bin/sh
            # Hyper OS Core Initialization
            echo "STAGE 1 - POWER ON"
            echo "STAGE 2 - CORE WAKE"
            echo "STAGE 3 - HYPER AI SYNC"
            echo "STAGE 4 - PREPARING ENVIRONMENT"
            echo "STAGE 5 - BRAND REVEAL"
            echo "ACTIVE SECURE CHANNELS"
        """.trimIndent())
    }

    fun getFilesAtDirectory(dirPath: String): List<OSFile> {
        val canonicalDir = if (dirPath.endsWith("/")) dirPath else "$dirPath/"
        return files.filter { file ->
            if (file.path == dirPath) return@filter false
            val parentPath = file.path.substringBeforeLast("/") + "/"
            val parentPathAdjusted = if (parentPath == "//") "/" else parentPath
            val targetFolder = if (dirPath == "/") "/" else "$dirPath/"
            
            if (dirPath == "/") {
                // Return top level roots only (e.g. /User, /System, /Cloud)
                file.path.count { it == '/' } == 1
            } else {
                parentPathAdjusted == targetFolder || file.path.removeSuffix("/" + file.name) == dirPath
            }
        }.distinctBy { it.path }
    }

    fun createFile(filePath: String, content: String): Boolean {
        if (files.any { it.path == filePath }) return false // Already exists
        val name = filePath.substringAfterLast("/")
        files.add(OSFile(name = name, content = content, isDirectory = false, path = filePath))
        return true
    }

    fun createDirectory(dirPath: String): Boolean {
        if (files.any { it.path == dirPath }) return false
        val name = dirPath.substringAfterLast("/")
        files.add(OSFile(name = name, content = "", isDirectory = true, path = dirPath))
        return true
    }

    fun deleteFile(filePath: String): Boolean {
        return files.removeIf { it.path == filePath }
    }

    fun updateFileContent(filePath: String, newContent: String): Boolean {
        val file = files.find { it.path == filePath }
        if (file != null && !file.isDirectory) {
            file.content = newContent
            // Force compose update by replacing or triggering replacement
            val idx = files.indexOf(file)
            if (idx >= 0) {
                files[idx] = file.copy(content = newContent)
            }
            return true
        }
        return false
    }
}
