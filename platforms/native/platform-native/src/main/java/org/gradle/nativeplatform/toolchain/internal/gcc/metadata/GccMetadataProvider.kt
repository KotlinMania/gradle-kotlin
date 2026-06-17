/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.gcc.metadata

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.internal.FileUtils
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.io.StreamByteBuffer
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.VersionNumber
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.nio.file.NoSuchFileException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number and default architecture by running with -dM -E -v and scraping the output.
 */
class GccMetadataProvider internal constructor(execActionFactory: ExecActionFactory?, private val compilerType: GccCompilerType) : AbstractMetadataProvider<GccMetadata?>(execActionFactory) {
    override fun getCompilerType(): CompilerType {
        return compilerType
    }

    override fun compilerArgs(): MutableList<String?> {
        return ImmutableList.of<String?>("-dM", "-E", "-v", "-")
    }

    override fun parseCompilerOutput(output: String, error: String, gccBinary: File, path: MutableList<File?>): GccMetadata {
        val defines = parseDefines(output, gccBinary)
        val scrapedVersion = determineVersion(defines, gccBinary)
        val architecture = determineArchitecture(defines)
        val scrapedVendor = determineVendor(error, scrapedVersion, gccBinary)
        val systemIncludes: ImmutableList<File?> = determineSystemIncludes(defines, path, error)

        return DefaultGccMetadata(scrapedVersion, scrapedVendor, architecture, systemIncludes)
    }

    private fun determineVendor(error: String, versionNumber: VersionNumber, gccBinary: File?): String {
        val reader = BufferedReader(StringReader(error))
        val majorMinorOnly = versionNumber.getMajor().toString() + "." + versionNumber.getMinor()
        var line: String
        try {
            while ((reader.readLine().also { line = it }) != null) {
                // allowing win32 to bypass the check is due to the mingw compiler on linux not including major.minor in the version string.
                if ((line.contains(majorMinorOnly) || line.contains("win32"))
                    && line.contains(" version ")
                    && line.contains(compilerType.getIdentifier())
                    && !line.contains(" default target ")
                ) {
                    return line
                }
            }
        } catch (e: IOException) {
            // Should not happen reading from a StringReader
            throw throwAsUncheckedException(e)
        }
        throw BrokenResultException(String.format("Could not determine %s metadata: could not find vendor in output of %s.", compilerType.getDescription(), gccBinary))
    }

    private fun determineSystemIncludes(defines: MutableMap<String?, String?>, path: MutableList<File?>, error: String): ImmutableList<File?> {
        var cygpathExe: File? = null
        val isCygwin = defines.containsKey("__CYGWIN__")
        if (isCygwin) {
            cygpathExe = findCygpath(path)
        }

        val reader = BufferedReader(StringReader(error))
        var line: String?
        val builder = ImmutableList.builder<File?>()
        var systemIncludesStarted = false
        try {
            while ((reader.readLine().also { line = it }) != null) {
                if (SYSTEM_INCLUDES_END == line) {
                    break
                }
                if (SYSTEM_INCLUDES_START == line) {
                    systemIncludesStarted = true
                    continue
                }
                if (systemIncludesStarted) {
                    // Exclude frameworks for CLang - they need to be handled differently
                    if (compilerType == GccCompilerType.CLANG && line!!.contains(FRAMEWORK_INCLUDE)) {
                        continue
                    }
                    // Exclude framework directories for GCC - they are added as system search paths but they are actually not
                    if (compilerType == GccCompilerType.GCC && line!!.endsWith("/Library/Frameworks")) {
                        continue
                    }
                    var include = line!!.trim { it <= ' ' }
                    if (isCygwin) {
                        include = mapCygwinPath(cygpathExe!!, include)
                    }
                    var realPath = File(include)
                    try {
                        realPath = realPath.toPath().toRealPath().toFile()
                    } catch (ignore: NoSuchFileException) {
                        // resolve the potential symlink, if not found, fallback to do nothing.
                    }
                    builder.add(FileUtils.normalize(realPath))
                }
            }
            return builder.build()
        } catch (e: IOException) {
            // Should not happen reading from a StringReader
            throw throwAsUncheckedException(e)
        }
    }

    private fun findCygpath(path: MutableList<File?>): File {
        for (dir in path) {
            val exe = File(dir, OperatingSystem.current()!!.getExecutableName("cygpath"))
            if (exe.exists()) {
                return exe
            }
        }
        val exe = OperatingSystem.current()!!.findInPath("cygpath")
        if (exe != null) {
            return exe
        }
        throw IllegalStateException("Could not find 'cygpath' executable in path: " + Joiner.on(File.pathSeparator).join(path))
    }

    private fun mapCygwinPath(cygpathExe: File, cygwinPath: String?): String {
        val execAction = getExecActionFactory().newExecAction()
        execAction!!.setWorkingDir(File(".").getAbsolutePath())
        execAction.commandLine(cygpathExe.getAbsolutePath(), "-w", cygwinPath)
        val buffer = StreamByteBuffer()
        val errorBuffer = StreamByteBuffer()
        execAction.setStandardOutput(buffer.outputStream)
        execAction.setErrorOutput(errorBuffer.outputStream)
        execAction.execute()!!.assertNormalExitValue()
        return buffer.readAsString().trim { it <= ' ' }
    }

    private fun parseDefines(output: String, gccBinary: File): MutableMap<String?, String?> {
        val reader = BufferedReader(StringReader(output))
        var line: String?
        val defines: MutableMap<String?, String?> = HashMap<String?, String?>()
        try {
            while ((reader.readLine().also { line = it }) != null) {
                val matcher: Matcher = DEFINE_PATTERN.matcher(line)
                if (!matcher.matches()) {
                    throw BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()))
                }
                defines.put(matcher.group(1), matcher.group(2))
            }
        } catch (e: IOException) {
            // Should not happen reading from a StringReader
            throw throwAsUncheckedException(e)
        }
        if (!defines.containsKey("__GNUC__") && !defines.containsKey("__clang__")) {
            throw BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()))
        }
        return defines
    }

    private fun determineVersion(defines: MutableMap<String?, String?>, gccBinary: File): VersionNumber {
        val major: Int
        val minor: Int
        val patch: Int
        when (compilerType) {
            GccCompilerType.CLANG -> {
                if (!defines.containsKey("__clang__")) {
                    throw BrokenResultException(String.format("%s appears to be GCC rather than Clang. Treating it as GCC.", gccBinary.getName()))
                }
                major = toInt(defines.get("__clang_major__"))
                minor = toInt(defines.get("__clang_minor__"))
                patch = toInt(defines.get("__clang_patchlevel__"))
            }

            GccCompilerType.GCC -> {
                if (defines.containsKey("__clang__")) {
                    throw BrokenResultException(String.format("XCode %s is a wrapper around Clang. Treating it as Clang and not GCC.", gccBinary.getName()))
                }
                major = toInt(defines.get("__GNUC__"))
                minor = toInt(defines.get("__GNUC_MINOR__"))
                patch = toInt(defines.get("__GNUC_PATCHLEVEL__"))
            }

            else -> throw GradleException("Unknown compiler type " + compilerType)
        }
        return VersionNumber(major, minor, patch, null)
    }

    private fun determineArchitecture(defines: MutableMap<String?, String?>): ArchitectureInternal {
        val i386 = defines.containsKey("__i386__")
        val amd64 = defines.containsKey("__amd64__")
        val architecture: ArchitectureInternal
        if (i386) {
            architecture = Architectures.forInput("i386")
        } else if (amd64) {
            architecture = Architectures.forInput("amd64")
        } else {
            architecture = DefaultNativePlatform.getCurrentArchitecture()
        }
        return architecture
    }

    private fun toInt(value: String?): Int {
        if (value == null) {
            return 0
        }
        try {
            return value.toInt()
        } catch (e: NumberFormatException) {
            return 0
        }
    }

    private class DefaultGccMetadata(
        private val scrapedVersion: VersionNumber?,
        private val scrapedVendor: String?,
        private val architecture: ArchitectureInternal?,
        private val systemIncludes: ImmutableList<File?>?
    ) : GccMetadata, SystemLibraries {
        override fun getVersion(): VersionNumber? {
            return scrapedVersion
        }

        override fun getSystemLibraries(): SystemLibraries {
            return this
        }

        val includeDirs: MutableList<File?>?
            get() = systemIncludes

        val libDirs: MutableList<File?>
            get() = mutableListOf<File?>()

        val preprocessorMacros: MutableMap<String?, String?>
            get() = mutableMapOf<String?, String?>()

        override fun getDefaultArchitecture(): ArchitectureInternal? {
            return architecture
        }

        override fun getVendor(): String? {
            return scrapedVendor
        }
    }

    companion object {
        private val DEFINE_PATTERN: Pattern = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)")
        private const val SYSTEM_INCLUDES_START = "#include <...> search starts here:"
        private const val SYSTEM_INCLUDES_END = "End of search list."
        private const val FRAMEWORK_INCLUDE = " (framework directory)"
        @JvmStatic
        fun forGcc(execActionFactory: ExecActionFactory?): GccMetadataProvider {
            return GccMetadataProvider(execActionFactory, GccCompilerType.GCC)
        }

        @JvmStatic
        fun forClang(execActionFactory: ExecActionFactory?): GccMetadataProvider {
            return GccMetadataProvider(execActionFactory, GccCompilerType.CLANG)
        }
    }
}
