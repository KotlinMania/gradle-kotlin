/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.plugins

import org.gradle.api.Action
import org.gradle.internal.IoActions
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails
import org.gradle.jvm.application.scripts.ScriptGenerator
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.CollectionUtils.toStringList
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class StartScriptGenerator internal constructor(
    private val unixStartScriptGenerator: ScriptGenerator,
    private val windowsStartScriptGenerator: ScriptGenerator,
    private val unixFileOperation: UnixFileOperation
) {
    private var applicationName: String? = null
    private var gitRef: String? = null
    private var optsEnvironmentVar: String? = null
    private var exitEnvironmentVar: String? = null
    private var entryPoint: AppEntryPoint? = null
    private var defaultJvmOpts: Iterable<String?> = mutableListOf<String?>()
    private var classpath: Iterable<String?>? = null
    private var modulePath: Iterable<String?> = mutableListOf<String?>()
    private var scriptRelPath: String? = null
    private var appNameSystemProperty: String? = null

    fun setApplicationName(applicationName: String?) {
        this.applicationName = applicationName
    }

    fun setGitRef(gitRef: String?) {
        this.gitRef = gitRef
    }

    fun setOptsEnvironmentVar(optsEnvironmentVar: String?) {
        this.optsEnvironmentVar = optsEnvironmentVar
    }

    fun setExitEnvironmentVar(exitEnvironmentVar: String?) {
        this.exitEnvironmentVar = exitEnvironmentVar
    }

    /**
     * Sets the main class name to be used when generating the start script.
     *
     *
     *
     * Mutually exclusive with [.setEntryPoint].
     *
     *
     * @param mainClassName the main class name to be used when generating the start script
     */
    fun setMainClassName(mainClassName: String?) {
        this.entryPoint = MainClass(mainClassName!!)
    }

    /**
     * Sets the entry point to be used when generating the start script.
     *
     *
     *
     * Mutually exclusive with [.setMainClassName].
     *
     *
     * @param entryPoint the entry point to be used when generating the start script
     */
    fun setEntryPoint(entryPoint: AppEntryPoint?) {
        this.entryPoint = entryPoint
    }

    fun setDefaultJvmOpts(defaultJvmOpts: Iterable<String?>) {
        this.defaultJvmOpts = defaultJvmOpts
    }

    fun setClasspath(classpath: Iterable<String?>) {
        this.classpath = classpath
    }

    fun setModulePath(modulePath: Iterable<String?>) {
        this.modulePath = modulePath
    }

    fun setScriptRelPath(scriptRelPath: String?) {
        this.scriptRelPath = scriptRelPath
    }

    fun setAppNameSystemProperty(appNameSystemProperty: String?) {
        this.appNameSystemProperty = appNameSystemProperty
    }

    @JvmOverloads
    constructor(unixStartScriptGenerator: ScriptGenerator = UnixStartScriptGenerator(), windowsStartScriptGenerator: ScriptGenerator = WindowsStartScriptGenerator()) : this(
        unixStartScriptGenerator,
        windowsStartScriptGenerator,
        DefaultUnixFileOperation()
    )

    private fun createStartScriptGenerationDetails(): JavaAppStartScriptGenerationDetails {
        return DefaultJavaAppStartScriptGenerationDetails(
            applicationName!!,
            gitRef!!,
            optsEnvironmentVar!!,
            exitEnvironmentVar!!,
            entryPoint!!,
            toStringList(defaultJvmOpts),
            CollectionUtils.toStringList(classpath!!),
            toStringList(modulePath),
            scriptRelPath!!,
            appNameSystemProperty
        )
    }

    fun generateUnixScript(unixScript: File) {
        IoActions.writeTextFile(unixScript, StandardCharsets.UTF_8.name(), Generate(createStartScriptGenerationDetails(), unixStartScriptGenerator))
        unixFileOperation.createExecutablePermission(unixScript)
    }

    fun generateWindowsScript(windowsScript: File) {
        IoActions.writeTextFile(windowsScript, StandardCharsets.UTF_8.name(), Generate(createStartScriptGenerationDetails(), windowsStartScriptGenerator))
    }

    internal interface UnixFileOperation {
        fun createExecutablePermission(file: File?)
    }

    private class DefaultUnixFileOperation : UnixFileOperation {
        override fun createExecutablePermission(file: File) {
            if (current()!!.isWindows) {
                // Windows has no POSIX permissions. Matches the previous Ant Chmod behavior, which also skipped on non-Unix:
                // https://github.com/apache/ant/blob/5db231018603fbb23a66f43304003cb1451f20cc/src/main/org/apache/tools/ant/taskdefs/Chmod.java#L265-L269
                return
            }
            val path = file.toPath()
            try {
                val permissions = Files.getPosixFilePermissions(path)
                permissions.add(PosixFilePermission.OWNER_READ)
                permissions.add(PosixFilePermission.OWNER_EXECUTE)
                permissions.add(PosixFilePermission.GROUP_READ)
                permissions.add(PosixFilePermission.GROUP_EXECUTE)
                permissions.add(PosixFilePermission.OTHERS_READ)
                permissions.add(PosixFilePermission.OTHERS_EXECUTE)
                Files.setPosixFilePermissions(path, permissions)
            } catch (e: UnsupportedOperationException) {
                // Filesystem does not support POSIX permissions (rare on non-Windows, but possible on some
                // network mounts and exotic filesystems). Fall back to File.setExecutable so the script is
                // still runnable; group/other bits are silently dropped.
                if (!file.setExecutable(true, false)) {
                    throw RuntimeException("Could not make " + file + " executable on a filesystem without POSIX support.")
                }
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
    }

    private class Generate(private val startScriptGenerationDetails: JavaAppStartScriptGenerationDetails, private val unixStartScriptGenerator: ScriptGenerator) : Action<BufferedWriter?> {
        override fun execute(writer: BufferedWriter) {
            unixStartScriptGenerator.generateScript(startScriptGenerationDetails, writer)
        }
    }
}
