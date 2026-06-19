/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.os

import org.gradle.internal.scan.UsedByScanPlugin
import java.io.File
import java.util.LinkedList
import java.util.regex.Pattern
import kotlin.math.max

abstract class OperatingSystem internal constructor() {
    private val toStringValue: String
    val name: String
    val version: String

    init {
        this.name = System.getProperty("os.name")
        this.version = System.getProperty("os.version")
        toStringValue = this.name + " " + this.version + " " + System.getProperty("os.arch")
    }

    override fun toString(): String {
        return toStringValue
    }

    @get:UsedByScanPlugin
    open val isWindows: Boolean
        get() = false

    open val isUnix: Boolean
        get() = false

    open val isMacOsX: Boolean
        get() = false

    open val isLinux: Boolean
        get() = false

    abstract val nativePrefix: String

    abstract fun getScriptName(scriptPath: String): String

    abstract fun getExecutableName(executablePath: String): String

    abstract val executableSuffix: String

    abstract fun getSharedLibraryName(libraryName: String): String

    abstract val sharedLibrarySuffix: String

    abstract fun getStaticLibraryName(libraryName: String): String

    abstract val staticLibrarySuffix: String

    abstract val linkLibrarySuffix: String

    abstract fun getLinkLibraryName(libraryPath: String): String

    @get:UsedByScanPlugin
    abstract val familyName: String

    /**
     * Locates the given executable in the system path. Returns null if not found.
     */
    fun findInPath(name: String): File? {
        val exeName = getExecutableName(name)
        if (exeName.contains(File.separator)) {
            val candidate = File(exeName)
            if (candidate.isFile()) {
                return candidate
            }
            return null
        }
        for (dir in this.path!!) {
            val candidate: File = File(dir, exeName)
            if (candidate.isFile()) {
                return candidate
            }
        }

        return null
    }

    fun findAllInPath(name: String): MutableList<File?> {
        val all: MutableList<File?> = LinkedList<File?>()

        for (dir in this.path!!) {
            val candidate: File = File(dir, name)
            if (candidate.isFile()) {
                all.add(candidate)
            }
        }

        return all
    }

    open val path: MutableList<File?>?
        get() {
            val path = System.getenv(this.pathVar)
            if (path == null) {
                return mutableListOf<File?>()
            }
            val entries: MutableList<File?> = ArrayList<File?>()
            for (entry in path.split(Pattern.quote(File.pathSeparator).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                entries.add(File(entry))
            }
            return entries
        }

    open val pathVar: String
        get() = "PATH"

    open class Windows : OperatingSystem() {
        private val nativePrefixValue: String

        init {
            nativePrefixValue = resolveNativePrefix()
        }

        override val isWindows: Boolean
            get() = true

        override val familyName: String
            get() = "windows"

        override val executableSuffix: String
            get() = ".exe"

        override val sharedLibrarySuffix: String
            get() = ".dll"

        override val linkLibrarySuffix: String
            get() = ".lib"

        override val staticLibrarySuffix: String
            get() = ".lib"

        override val nativePrefix: String
            get() = nativePrefixValue

        override val pathVar: String
            get() = "Path"

        override fun getScriptName(scriptPath: String): String {
            return withExtension(scriptPath, ".bat")
        }

        override fun getExecutableName(executablePath: String): String {
            return withExtension(executablePath, ".exe")
        }

        override fun getSharedLibraryName(libraryPath: String): String {
            return withExtension(libraryPath, ".dll")
        }

        override fun getLinkLibraryName(libraryPath: String): String {
            return withExtension(libraryPath, ".lib")
        }

        override fun getStaticLibraryName(libraryName: String): String {
            return withExtension(libraryName, ".lib")
        }

        private fun resolveNativePrefix(): String {
            var arch = System.getProperty("os.arch")
            if ("i386" == arch) {
                arch = "x86"
            }
            return "win32-" + arch
        }

        companion object {
            private fun withExtension(filePath: String, extension: String): String {
                if (filePath.lowercase().endsWith(extension)) {
                    return filePath
                }
                val lastFileSeparator = max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'))
                val lastDot = filePath.lastIndexOf('.')
                val base = if (lastDot > lastFileSeparator) filePath.substring(0, lastDot) else filePath
                return base + extension
            }
        }
    }

    open class Unix : OperatingSystem() {
        private val nativePrefixValue: String

        init {
            this.nativePrefixValue = resolveNativePrefix()
        }

        override val isUnix: Boolean
            get() = true

        override val familyName: String
            get() = "unknown"

        override val executableSuffix: String
            get() = ""

        override val sharedLibrarySuffix: String
            get() = ".so"

        override val linkLibrarySuffix: String
            get() = sharedLibrarySuffix

        override val staticLibrarySuffix: String
            get() = ".a"

        override val nativePrefix: String
            get() = nativePrefixValue

        override fun getScriptName(scriptPath: String): String {
            return scriptPath
        }

        override fun getExecutableName(executablePath: String): String {
            return executablePath
        }

        override fun getSharedLibraryName(libraryName: String): String {
            return getLibraryName(libraryName, sharedLibrarySuffix)
        }

        private fun getLibraryName(libraryName: String, suffix: String): String {
            if (libraryName.endsWith(suffix)) {
                return libraryName
            }
            val pos = libraryName.lastIndexOf('/')
            if (pos >= 0) {
                return libraryName.substring(0, pos + 1) + "lib" + libraryName.substring(pos + 1) + suffix
            } else {
                return "lib" + libraryName + suffix
            }
        }

        override fun getLinkLibraryName(libraryPath: String): String {
            return getSharedLibraryName(libraryPath)
        }

        override fun getStaticLibraryName(libraryName: String): String {
            return getLibraryName(libraryName, ".a")
        }

        private fun resolveNativePrefix(): String {
            val arch = this.getArch()
            var osPrefix = this.getOsPrefix()
            osPrefix += "-" + arch
            return osPrefix
        }

        protected open fun getArch(): String {
            var arch = System.getProperty("os.arch")
            if ("x86" == arch) {
                arch = "i386"
            }
            if ("x86_64" == arch) {
                arch = "amd64"
            }
            if ("powerpc" == arch) {
                arch = "ppc"
            }
            return arch
        }

        protected open fun getOsPrefix(): String {
            var osPrefix = this.name.lowercase()
            val space = osPrefix.indexOf(" ")
            if (space != -1) {
                osPrefix = osPrefix.substring(0, space)
            }
            return osPrefix
        }
    }

    class MacOs : Unix() {
        override val isMacOsX: Boolean
            get() = true

        override val familyName: String
            get() = "os x"

        override val sharedLibrarySuffix: String
            get() = ".dylib"

        override val nativePrefix: String
            get() = "darwin"
    }

    class Linux : Unix() {
        override val isLinux: Boolean
            get() = true

        override val familyName: String
            get() = "linux"
    }

    class FreeBSD : Unix()

    class Solaris : Unix() {
        override val familyName: String
            get() = "solaris"

        override fun getOsPrefix(): String {
            return "sunos"
        }

        override fun getArch(): String {
            val arch = System.getProperty("os.arch")
            if (arch == "i386" || arch == "x86") {
                return "x86"
            }
            return super.getArch()
        }
    }

    companion object {
        @JvmField
        val WINDOWS: Windows = Windows()
        val MAC_OS: MacOs = MacOs()
        @JvmField
        val SOLARIS: Solaris = Solaris()
        val LINUX: Linux = Linux()
        @JvmField
        val FREE_BSD: FreeBSD = FreeBSD()
        val UNIX: Unix = Unix()
        private var currentOs: OperatingSystem? = null

        @JvmStatic
        fun current(): OperatingSystem {
            if (currentOs == null) {
                currentOs = forName(System.getProperty("os.name"))
            }
            return currentOs!!
        }

        // for testing current()
        @JvmStatic
        fun resetCurrent() {
            currentOs = null
        }

        @JvmStatic
        fun forName(os: String): OperatingSystem {
            val osName = os.lowercase()
            if (osName.contains("windows")) {
                return WINDOWS
            } else if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) {
                return MAC_OS
            } else if (osName.contains("sunos") || osName.contains("solaris")) {
                return SOLARIS
            } else if (osName.contains("linux")) {
                return LINUX
            } else if (osName.contains("freebsd")) {
                return FREE_BSD
            } else {
                // Not strictly true
                return UNIX
            }
        }
    }
}
