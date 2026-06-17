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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.nativeplatform.internal.DefaultLinkerSpec
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable

/**
 * Links a binary shared library from object files and imported libraries.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class LinkSharedLibrary : AbstractLinkTask() {
    init {
        this.importLibrary.convention(getProject().getLayout().getProjectDirectory().file(getProject().getProviders().provider<String?>(object : Callable<String?> {
            @Throws(Exception::class)
            override fun call(): String? {
                val binaryFile = getLinkedFile().getOrNull()
                if (binaryFile == null) {
                    return null
                }
                val toolChain = getToolChain().getOrNull() as NativeToolChainInternal?
                val targetPlatform = getTargetPlatform().getOrNull() as NativePlatformInternal?
                if (toolChain == null || targetPlatform == null) {
                    return null
                }
                val toolProvider = toolChain.select(targetPlatform)
                if (!toolProvider!!.producesImportLibrary()) {
                    return null
                }
                return toolProvider.getImportLibraryName(binaryFile.getAsFile().getAbsolutePath())
            }
        })))
    }

    @JvmField
    @get:OutputFile
    @get:Optional
    abstract val importLibrary: RegularFileProperty?

    @JvmField
    @get:Input
    @get:Optional
    abstract val installName: Property<String?>?

    override fun createLinkerSpec(): LinkerSpec {
        val spec = Spec()
        spec.setInstallName(this.installName.getOrNull())
        spec.setImportLibrary(this.importLibrary.getAsFile().getOrNull())
        return spec
    }

    private class Spec : DefaultLinkerSpec(), SharedLibraryLinkerSpec {
        private var installName: String? = null
        private var importLibrary: File? = null

        override fun getInstallName(): String? {
            return installName
        }

        fun setInstallName(installName: String?) {
            this.installName = installName
        }

        override fun getImportLibrary(): File? {
            return importLibrary
        }

        fun setImportLibrary(importLibrary: File?) {
            this.importLibrary = importLibrary
        }
    }
}
