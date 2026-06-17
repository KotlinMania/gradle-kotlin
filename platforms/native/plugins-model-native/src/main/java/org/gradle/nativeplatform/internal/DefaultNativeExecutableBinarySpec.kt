/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.internal

import org.gradle.nativeplatform.NativeExecutableBinary
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableFileSpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeInstallationSpec
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper
import java.io.File

class DefaultNativeExecutableBinarySpec : AbstractNativeBinarySpec(), NativeExecutableBinary, NativeExecutableBinarySpecInternal {
    private val tasks = DefaultTasksCollection(super.getTasks())
    private val installation = NativeInstallationSpec()
    private val executable = NativeExecutableFileSpec()

    override fun getComponent(): NativeExecutableSpec? {
        return getComponentAs<NativeExecutableSpec?>(NativeExecutableSpec::class.java)
    }

    override fun getApplication(): NativeExecutableSpec? {
        return getComponentAs<NativeExecutableSpec?>(NativeExecutableSpec::class.java)
    }

    override fun getExecutable(): NativeExecutableFileSpec {
        return executable
    }

    override fun getExecutableFile(): File? {
        return getExecutable().getFile()
    }

    override fun getInstallation(): NativeInstallationSpec {
        return installation
    }

    override fun getPrimaryOutput(): File? {
        return getExecutableFile()
    }

    override fun getCreateOrLink(): ObjectFilesToBinary? {
        return tasks.getLink()
    }

    override fun getTasks(): NativeExecutableBinarySpec.TasksCollection {
        return tasks
    }

    private class DefaultTasksCollection(delegate: BinaryTasksCollection?) : BinaryTasksCollectionWrapper(delegate), NativeExecutableBinarySpec.TasksCollection {
        override fun getLink(): LinkExecutable? {
            return findSingleTaskWithType<LinkExecutable?>(LinkExecutable::class.java)
        }

        override fun getInstall(): InstallExecutable? {
            return findSingleTaskWithType<InstallExecutable?>(InstallExecutable::class.java)
        }
    }
}
