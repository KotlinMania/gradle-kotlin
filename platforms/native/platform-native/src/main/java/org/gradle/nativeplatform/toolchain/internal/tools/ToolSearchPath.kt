/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.nativeplatform.toolchain.internal.tools

import org.gradle.api.GradleException
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.toolchain.internal.ToolType
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class ToolSearchPath(private val operatingSystem: OperatingSystem) {
    private val executables: MutableMap<String?, File?> = HashMap<String?, File?>()
    val path: MutableList<File> = ArrayList<File>()

    fun setPath(pathEntries: MutableList<File?>) {
        this.path.clear()
        this.path.addAll(pathEntries)
        executables.clear()
    }

    fun path(pathEntry: File?) {
        path.add(pathEntry!!)
        executables.clear()
    }

    fun locate(key: ToolType, exeName: String): CommandLineToolSearchResult {
        var executable = executables.get(exeName)
        if (executable == null) {
            executable = findExecutable(operatingSystem, exeName)
            if (executable != null) {
                executables.put(exeName, executable)
            }
        }
        return if (executable == null || !executable.isFile()) MissingTool(key, exeName, this.path) else FoundTool(executable)
    }

    private fun findExecutable(operatingSystem: OperatingSystem, name: String): File? {
        val path = if (path.isEmpty()) operatingSystem.path else this.path
        val exeName = operatingSystem.getExecutableName(name)
        try {
            if (name.contains(File.separator)) {
                return maybeResolveFile(operatingSystem, File(name), File(exeName))
            }
            for (pathEntry in path!!) {
                val resolved = maybeResolveFile(operatingSystem, File(pathEntry, name), File(pathEntry, exeName))
                if (resolved != null) {
                    return resolved
                }
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }

        return null
    }

    @Throws(IOException::class)
    private fun maybeResolveFile(operatingSystem: OperatingSystem, symlinkCandidate: File, exeCandidate: File): File? {
        if (exeCandidate.isFile()) {
            return exeCandidate
        }
        if (operatingSystem.isWindows) {
            val symlink = maybeResolveCygwinSymlink(symlinkCandidate)
            if (symlink != null) {
                return symlink
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun maybeResolveCygwinSymlink(symlink: File): File? {
        var symlink = symlink
        if (!symlink.isFile()) {
            return null
        }
        if (symlink.length() <= 11) {
            return null
        }

        var pathStr: String?
        val instr = DataInputStream(BufferedInputStream(FileInputStream(symlink)))
        try {
            val header = ByteArray(10)
            instr.readFully(header)
            if (String(header, StandardCharsets.UTF_8) != "!<symlink>") {
                return null
            }
            val pathContent = ByteArray(symlink.length().toInt() - 11)
            instr.readFully(pathContent)
            pathStr = String(pathContent, StandardCharsets.UTF_8)
        } finally {
            instr.close()
        }

        symlink = File(symlink.getParentFile(), pathStr)
        if (symlink.isFile()) {
            return symlink.getCanonicalFile()
        }
        return null
    }

    private class FoundTool(private val tool: File?) : CommandLineToolSearchResult {
        val isAvailable: Boolean
            get() = true

        override fun getTool(): File? {
            return tool
        }

        override fun explain(visitor: DiagnosticsVisitor?) {
        }
    }

    private class MissingTool(private val type: ToolType, private val exeName: String?, private val path: MutableList<File>) : CommandLineToolSearchResult {
        override fun explain(visitor: DiagnosticsVisitor) {
            if (path.isEmpty()) {
                visitor.node(String.format("Could not find %s '%s' in system path.", type.toolName, exeName))
            } else {
                visitor.node(String.format("Could not find %s '%s'. Searched in", type.toolName, exeName))
                visitor.startChildren()
                for (location in path) {
                    visitor.node(location.toString())
                }
                visitor.endChildren()
            }
        }

        override fun getTool(): File? {
            val formatter = TreeFormatter()
            explain(formatter)
            throw GradleException(formatter.toString())
        }

        val isAvailable: Boolean
            get() = false
    }
}
