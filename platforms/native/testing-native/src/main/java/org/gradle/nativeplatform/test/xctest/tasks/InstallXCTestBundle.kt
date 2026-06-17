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
package org.gradle.nativeplatform.test.xctest.tasks

import com.google.common.io.Files
import org.apache.commons.io.FilenameUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.CopySpec
import org.gradle.api.file.RegularFile
import org.gradle.api.file.SyncSpec
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.file.Chmod.chmod
import org.gradle.language.swift.internal.DefaultSwiftComponent.getName
import org.gradle.process.ExecResult.assertNormalExitValue
import org.gradle.process.ExecSpec
import org.gradle.util.internal.GFileUtils
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject

/**
 * Creates a XCTest bundle with a run script so it can be easily executed.
 *
 * @since 4.4
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class InstallXCTestBundle : DefaultTask() {
    init {
        // A work around for not being able to skip the task when an input _file_ does not exist
        dependsOn(this.bundleBinaryFile)
    }

    @get:Inject
    protected abstract val swiftStdlibToolLocator: SwiftStdlibToolLocator?

    @get:Inject
    protected abstract val fileSystem: FileSystem?

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations?

    @TaskAction
    @Throws(IOException::class)
    protected fun install() {
        val bundleFile = this.bundleBinaryFile.get().getAsFile()
        val bundleDir = this.installDirectory.get().file(bundleFile.getName() + ".xctest").getAsFile()
        installToDir(bundleDir, bundleFile)

        val runScript = this.runScriptFile.get().getAsFile()
        val runScriptText =
            ("#!/bin/sh"
                    + "\nAPP_BASE_NAME=`dirname \"$0\"`"
                    + "\nXCTEST_LOCATION=`xcrun --find xctest`"
                    + "\nexec \"\$XCTEST_LOCATION\" \"$@\" \"\$APP_BASE_NAME/" + bundleDir.getName() + "\""
                    + "\n")

        GFileUtils.writeFile(runScriptText, runScript)
        this.fileSystem.chmod(runScript, 493)
    }

    @Throws(IOException::class)
    private fun installToDir(bundleDir: File, bundleFile: File) {
        this.fileSystemOperations.sync(SerializableLambdas.action<SyncSpec?>(SerializableLambdas.SerializableAction { topSpec: SyncSpec? ->
            topSpec!!.from(bundleFile, SerializableLambdas.action<CopySpec?>(SerializableLambdas.SerializableAction { spec: CopySpec? -> spec!!.into("Contents/MacOS") }))
            topSpec.into(bundleDir)
        }))

        val outputFile = File(bundleDir, "Contents/Info.plist")

        Files.asCharSink(outputFile, Charset.forName("UTF-8")).write(
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                    + "<plist version=\"1.0\">\n"
                    + "<dict/>\n"
                    + "</plist>")
        )

        this.execOperations.exec(SerializableLambdas.action<ExecSpec?>(SerializableLambdas.SerializableAction { execSpec: ExecSpec? ->
            execSpec!!.setWorkingDir(bundleDir)
            execSpec.executable(this.swiftStdlibToolLocator.find())
            execSpec.args(
                "--copy",
                "--scan-executable", bundleFile.getAbsolutePath(),
                "--destination", File(bundleDir, "Contents/Frameworks").getAbsolutePath(),
                "--platform", "macosx",
                "--resource-destination", File(bundleDir, "Contents/Resources").getAbsolutePath(),
                "--scan-folder", File(bundleDir, "Contents/Frameworks").getAbsolutePath()
            )
        })).assertNormalExitValue()
    }

    @get:Internal
    val runScriptFile: Provider<RegularFile?>
        /**
         * Returns the script file that can be used to run the install image.
         */
        get() = this.installDirectory.file(
            this.bundleBinaryFile.getLocationOnly()
                .map<String?>(SerializableLambdas.transformer<String?, RegularFile?>(SerializableLambdas.SerializableTransformer { file: RegularFile? ->
                    FilenameUtils.removeExtension(file!!.getAsFile().getName())
                }))
        )

    @get:Internal("covered by getBundleBinary()")
    abstract val bundleBinaryFile: RegularFileProperty?

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    @get:SkipWhenEmpty
    protected val bundleBinary: File?
        get() {
            val bundle = this.bundleBinaryFile.get()
            val bundleFile = bundle.getAsFile()
            if (!bundleFile.exists()) {
                return null
            }
            return bundleFile
        }

    @get:OutputDirectory
    abstract val installDirectory: DirectoryProperty?

    @get:Inject
    protected abstract val execOperations: ExecOperations?
}
