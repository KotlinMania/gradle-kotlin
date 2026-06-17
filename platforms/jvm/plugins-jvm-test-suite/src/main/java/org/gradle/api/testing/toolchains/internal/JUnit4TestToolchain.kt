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

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

/**
 * A [JvmTestToolchain] for JUnit 4.
 *
 * @since 8.5
 */
abstract class JUnit4TestToolchain : JvmTestToolchain<JUnit4ToolchainParameters> {
    @get:Inject
    protected abstract val dependencyFactory: DependencyFactory?

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    override fun createTestFramework(task: Test): TestFramework {
        return this.objectFactory.newInstance<JUnitTestFramework>(JUnitTestFramework::class.java, task.getFilter(), task.getTemporaryDirFactory(), task.dryRun)
    }

    override fun getImplementationDependencies(): Iterable<Dependency> {
        return ImmutableSet.of<Dependency>(this.dependencyFactory.create(GROUP_NAME + ":" + getParameters().getVersion().get()))
    }

    companion object {
        /**
         * The default version of JUnit 4 to use for compiling and executing tests.
         */
        const val DEFAULT_VERSION: String = "4.13.2"
        private const val GROUP_NAME = "junit:junit"
    }
}
