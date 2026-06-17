/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SyncSpec
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.file.Chmod.chmod
import org.gradle.internal.os.OperatingSystem.Companion.forName
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.util.internal.GFileUtils
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Installs an executable with it's dependent libraries so it can be easily executed.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class InstallExecutable @Inject constructor(private val workerLeaseService: WorkerLeaseService) : DefaultTask() {
    private val libs: ConfigurableFileCollection

    /**
     * Injects a [WorkerLeaseService] instance.
     *
     * @since 4.2
     */
    init {
        this.libs = getProject().files()
        this.installedExecutable.convention(this.libDirectory.map<RegularFile?>(Transformer { directory: Directory? -> directory.file(this.executableFile.getAsFile().get().getName()) }))
        // A further work around for missing ability to skip task when input file is missing (see #getInputFileIfExists below)
        getInputs().file(this.executableFile)
    }

    @JvmField
    @get:Internal
    abstract val toolChain: Property<NativeToolChain>?

    @JvmField
    @get:Nested
    abstract val targetPlatform: Property<NativePlatform>?

    @JvmField
    @get:OutputDirectory
    abstract val installDirectory: DirectoryProperty?

    @JvmField
    @get:Internal("Covered by inputFileIfExists")
    abstract val executableFile: RegularFileProperty

    @get:OutputFile
    abstract val installedExecutable: RegularFileProperty?

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    @get:SkipWhenEmpty
    protected val inputFileIfExists: File?
        /**
         * Workaround for when the task is given an input file that doesn't exist
         *
         * @since 4.3
         */
        get() {
            val sourceFile = this.executableFile
            if (sourceFile.isPresent() && sourceFile.get().getAsFile().exists()) {
                return sourceFile.get().getAsFile()
            } else {
                return null
            }
        }

    /**
     * The library files that should be installed.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    fun getLibs(): FileCollection {
        return libs
    }

    fun setLibs(libs: FileCollection) {
        this.libs.setFrom(libs)
    }

    /**
     * Adds a set of library files to be installed. The provided libs object is evaluated as per [Project.files].
     */
    fun lib(libs: Any) {
        this.libs.from(libs)
    }

    @get:Internal("covered by getInstallDirectory")
    val runScriptFile: Provider<RegularFile?>
        /**
         * Returns the script file that can be used to run the install image.
         *
         * @since 4.4
         */
        get() = this.installDirectory.file(
            this.executableFile.getLocationOnly().map<String?>(Transformer { executableFile: RegularFile? ->
                forName(
                    this.targetPlatform.get().getOperatingSystem().getName()
                ).getScriptName(executableFile!!.getAsFile().getName())
            })
        )

    @get:Inject
    protected abstract val fileSystem: FileSystem?

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations?

    @TaskAction
    protected fun install() {
        val nativePlatform = this.targetPlatform.get()
        val executable = this.executableFile.get().getAsFile()
        val libDirectory = this.libDirectory.get().getAsFile()
        val runScript = this.runScriptFile.get().getAsFile()
        val libs: MutableCollection<File?> = getLibs().getFiles()

        // TODO: Migrate this to the worker API once the FileSystem and FileOperations services can be injected
        workerLeaseService.runAsIsolatedTask(Runnable {
            installToDir(libDirectory, executable, libs)
            if (nativePlatform.getOperatingSystem().isWindows()) {
                installWindows(executable, runScript)
            } else {
                installUnix(executable, runScript)
            }
        })
    }

    private val libDirectory: Provider<Directory?>
        get() = this.installDirectory.getLocationOnly().map<Directory?>(Transformer { dir: Directory? -> dir!!.dir("lib") })

    private fun installWindows(executable: File, runScript: File?) {
        val toolChainPath = StringBuilder()

        val toolChain = this.toolChain.get()
        if (toolChain is Gcc) {
            // Gcc on windows requires the path to be set
            toolChainPath.append("SET PATH=")
            for (pathEntry in toolChain.path!!) {
                toolChainPath.append(pathEntry.getAbsolutePath()).append(";")
            }

            toolChainPath.append("%PATH%")
        }

        val runScriptText =
            ("\n@echo off"
                    + "\nSETLOCAL"
                    + "\n" + toolChainPath
                    + "\nCALL \"%~dp0lib\\" + executable.getName() + "\" %*"
                    + "\nEXIT /B %ERRORLEVEL%"
                    + "\nENDLOCAL"
                    + "\n")
        GFileUtils.writeFile(runScriptText, runScript)
    }

    private fun installUnix(executable: File, runScript: File) {
        val runScriptText =
            ("#!/bin/sh"
                    + "\nAPP_BASE_NAME=`dirname \"$0\"`"
                    + "\nDYLD_LIBRARY_PATH=\"\$APP_BASE_NAME/lib\""
                    + "\nexport DYLD_LIBRARY_PATH"
                    + "\nLD_LIBRARY_PATH=\"\$APP_BASE_NAME/lib\""
                    + "\nexport LD_LIBRARY_PATH"
                    + "\nexec \"\$APP_BASE_NAME/lib/" + executable.getName() + "\" \"$@\""
                    + "\n")

        GFileUtils.writeFile(runScriptText, runScript)

        this.fileSystem.chmod(runScript, 493)
    }

    private fun installToDir(binaryDir: File, executableFile: File, libs: MutableCollection<File?>) {
        this.fileSystemOperations.sync(Action { copySpec: SyncSpec? ->
            copySpec!!.into(binaryDir)
            copySpec.from(executableFile)
            copySpec.from(libs)
        })
    }
}
