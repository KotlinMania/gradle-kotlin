/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.testing.toolchains.internal

import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

/**
 * A toolchain for testing JVM projects.  Toolchains provide the dependencies and test framework for a given JVM testing ecosystem.
 *
 * @since 8.5
 */
interface JvmTestToolchain<T : JvmTestToolchainParameters?> {
    /**
     * Creates a test framework for the given test task.
     */
    fun createTestFramework(task: Test): TestFramework?

    val compileOnlyDependencies: Iterable<Dependency>
        /**
         * Returns the dependencies required to compile the test sources associated with this toolchain.
         */
        get() = mutableListOf<Dependency>()

    val runtimeOnlyDependencies: Iterable<Dependency>
        /**
         * Returns the dependencies required only when executing the tests associated with this toolchain.
         */
        get() = mutableListOf<Dependency>()

    val implementationDependencies: Iterable<Dependency>
        /**
         * Returns the dependencies required to compile and execute the tests associated with this toolchain.
         */
        get() = mutableListOf<Dependency>()

    @get:Inject
    val parameters: T?
}
