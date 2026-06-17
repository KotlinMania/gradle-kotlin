/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.test.internal

import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeExecutableFileSpec
import org.gradle.nativeplatform.NativeInstallationSpec
import org.gradle.nativeplatform.internal.AbstractNativeBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.NativeTestSuiteSpec
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper
import java.io.File

open class DefaultNativeTestSuiteBinarySpec : AbstractNativeBinarySpec(), NativeTestSuiteBinarySpecInternal {
    private val tasks = DefaultTasksCollection(super.getTasks())
    private var testedBinary: NativeBinarySpecInternal? = null
    private val installation = NativeInstallationSpec()
    private val executable = NativeExecutableFileSpec()

    override fun getComponent(): NativeTestSuiteSpec? {
        return getComponentAs<NativeTestSuiteSpec?>(NativeTestSuiteSpec::class.java)
    }

    override fun getTestSuite(): NativeTestSuiteSpec {
        return getComponent()!!
    }

    override fun getTestedBinary(): NativeBinarySpec {
        return testedBinary!!
    }

    override fun setTestedBinary(testedBinary: NativeBinarySpecInternal) {
        this.testedBinary = testedBinary
        setTargetPlatform(testedBinary.getTargetPlatform())
        setToolChain(testedBinary.getToolChain())
        setPlatformToolProvider(testedBinary.getPlatformToolProvider())
        setBuildType(testedBinary.getBuildType())
        setFlavor(testedBinary.getFlavor())
    }

    override fun getExecutableFile(): File? {
        return getExecutable().getFile()
    }

    override fun getInstallation(): NativeInstallationSpec {
        return installation
    }

    override fun getExecutable(): NativeExecutableFileSpec {
        return executable
    }

    override fun getPrimaryOutput(): File? {
        return getExecutableFile()
    }

    override fun getCreateOrLink(): ObjectFilesToBinary? {
        return tasks.getLink()
    }

    override fun getTasks(): NativeTestSuiteBinarySpec.TasksCollection {
        return tasks
    }

    private class DefaultTasksCollection(delegate: BinaryTasksCollection?) : BinaryTasksCollectionWrapper(delegate), NativeTestSuiteBinarySpec.TasksCollection {
        override fun getLink(): LinkExecutable? {
            return findSingleTaskWithType<LinkExecutable?>(LinkExecutable::class.java)
        }

        override fun getInstall(): InstallExecutable? {
            return findSingleTaskWithType<InstallExecutable?>(InstallExecutable::class.java)
        }

        override fun getRun(): RunTestExecutable {
            return findSingleTaskWithType<RunTestExecutable>(RunTestExecutable::class.java)
        }
    }
}
