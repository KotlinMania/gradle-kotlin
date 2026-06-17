/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.AbstractBuildableComponentSpec
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskDependency
import org.gradle.platform.base.internal.ComponentSpecIdentifier
import java.io.File

class PreCompiledHeader(identifier: ComponentSpecIdentifier?) : AbstractBuildableComponentSpec(identifier!!, PreCompiledHeader::class.java) {
    @JvmField
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:IgnoreEmptyDirectories
    var pchObjects: FileCollection? = null

    @JvmField
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:Optional
    var prefixHeaderFile: File? = null

    @JvmField
    @get:Input
    @get:Optional
    var includeString: String? = null

    @get:Internal
    val objectFile: File?
        get() = if (pchObjects == null) null else pchObjects!!.getSingleFile()

    @Internal
    override fun getIdentifier(): ComponentSpecIdentifier? {
        return super.getIdentifier()
    }

    @Internal
    override fun getName(): String? {
        return super.getName()
    }

    @Internal
    override fun getProjectPath(): String? {
        return super.getProjectPath()
    }

    @Internal
    override fun getTypeName(): String? {
        return super.getTypeName()
    }

    @Internal
    override fun getDisplayName(): String? {
        return super.getDisplayName()
    }

    @Internal
    override fun getBuildTask(): Task? {
        return super.getBuildTask()
    }

    @Internal
    override fun getBuildDependencies(): TaskDependency {
        return super.getBuildDependencies()
    }

    @Internal
    override fun getCheckTask(): Task? {
        return super.getCheckTask()
    }
}
