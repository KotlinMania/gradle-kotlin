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
package org.gradle.language.swift.tasks.internal

import org.gradle.language.nativeplatform.internal.AbstractNativeCompileSpec
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec
import java.io.File

class DefaultSwiftCompileSpec : AbstractNativeCompileSpec(), SwiftCompileSpec {
    private var moduleName: String? = null
    private var moduleFile: File? = null
    private var sourceCompatibility: SwiftVersion? = null
    private var changedFiles: MutableCollection<File?>? = null

    override fun getModuleName(): String? {
        return moduleName
    }

    override fun setModuleName(moduleName: String?) {
        this.moduleName = moduleName
    }

    override fun getModuleFile(): File? {
        return moduleFile
    }

    override fun setModuleFile(moduleFile: File?) {
        this.moduleFile = moduleFile
    }

    override fun getSourceCompatibility(): SwiftVersion? {
        return sourceCompatibility
    }

    override fun setSourceCompatibility(sourceCompatibility: SwiftVersion?) {
        this.sourceCompatibility = sourceCompatibility
    }

    override fun getChangedFiles(): MutableCollection<File?>? {
        return changedFiles
    }

    override fun setChangedFiles(changedFiles: MutableCollection<File?>?) {
        this.changedFiles = changedFiles
    }
}
