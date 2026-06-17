/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.language.nativeplatform.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.language.swift.tasks.internal.SymbolHider
import org.gradle.process.ExecSpec
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Unexports the `main` entry point symbol in an object file, so the object file can be linked with an executable.
 *
 * @since 4.4
 */
@CacheableTask
abstract class UnexportMainSymbol : DefaultTask() {
    @JvmField
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val objects: ConfigurableFileCollection?

    @get:Internal
    val relocatedObjects: FileCollection
        /**
         * Collection of modified object files.
         *
         * @since 4.8
         */
        get() = this.outputDirectory.getAsFileTree()

    @JvmField
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty?

    @TaskAction
    protected fun unexport(inputChanges: InputChanges) {
        for (change in inputChanges.getFileChanges(this.objects)) {
            if (change.getChangeType() == ChangeType.REMOVED) {
                val relocatedFileLocation = relocatedObject(change.getFile())
                relocatedFileLocation.delete()
            } else {
                if (change.getFile().isFile()) {
                    unexportMainSymbol(change.getFile())
                }
            }
        }
    }

    private fun unexportMainSymbol(`object`: File) {
        val relocatedObject = relocatedObject(`object`)
        if (current()!!.isWindows) {
            try {
                val symbolHider = SymbolHider(`object`)
                symbolHider.hideSymbol("main") // 64 bit
                symbolHider.hideSymbol("_main") // 32 bit
                symbolHider.hideSymbol("wmain") // 64 bit
                symbolHider.hideSymbol("_wmain") // 32 bit
                symbolHider.saveTo(relocatedObject)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        } else {
            this.execOperations.exec(object : Action<ExecSpec?> {
                override fun execute(execSpec: ExecSpec) {
                    // TODO: should use target platform to make this decision
                    if (current()!!.isMacOsX) {
                        execSpec.executable("ld") // TODO: Locate this tool from a tool provider
                        execSpec.args(`object`)
                        execSpec.args("-o", relocatedObject)
                        execSpec.args("-r") // relink, produce another object file
                        execSpec.args("-unexported_symbol", "_main") // hide _main symbol
                    } else if (current()!!.isLinux) {
                        execSpec.executable("objcopy") // TODO: Locate this tool from a tool provider
                        execSpec.args("-L", "main") // hide main symbol
                        execSpec.args(`object`)
                        execSpec.args(relocatedObject)
                    } else {
                        throw IllegalStateException("Do not know how to unexport a main symbol on " + current())
                    }
                }
            })
        }
    }

    private fun relocatedObject(`object`: File): File {
        return this.outputDirectory.file(`object`.getName()).get().getAsFile()
    }

    @get:Inject
    protected abstract val execOperations: ExecOperations?
}
