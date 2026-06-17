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
package org.gradle.nativeplatform.test

import org.gradle.api.Incubating
import org.gradle.internal.HasInternalProtocol
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.testing.base.TestSuiteBinarySpec
import org.gradle.testing.base.TestSuiteTaskCollection

/**
 * An executable which runs a suite of tests.
 *
 * @since 4.2
 */
@Incubating
@HasInternalProtocol
interface NativeTestSuiteBinarySpec : TestSuiteBinarySpec, NativeBinarySpec {
    /**
     * Provides access to key tasks used for building the binary.
     */
    interface TasksCollection : TestSuiteTaskCollection {
        /**
         * The link task.
         */
        val link: Task?

        /**
         * The install task.
         */
        val install: Task?
    }

    /**
     * {@inheritDoc}
     */
    override fun getTestSuite(): NativeTestSuiteSpec

    override fun getComponent(): NativeTestSuiteSpec?

    /**
     * The tested binary.
     */
    override fun getTestedBinary(): NativeBinarySpec

    /**
     * The executable file.
     */
    val executableFile: File?

    /**
     * {@inheritDoc}
     */
    override fun getTasks(): TasksCollection?

    val installation: NativeInstallationSpec?

    val executable: NativeExecutableFileSpec?
}
