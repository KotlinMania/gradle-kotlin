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
import org.gradle.util.Path

/**
 * A [JvmTestToolchain] that caches the [TestFramework] instances it creates.  This prevents multiple calls
 * to [JvmTestToolchain.createTestFramework] for the same test task from overwriting the test framework options.
 *
 * @since 8.5
 */
class FrameworkCachingJvmTestToolchain<T : JvmTestToolchainParameters?>(private val delegate: JvmTestToolchain<T?>) : JvmTestToolchain<T?> {
    private val testFrameworks: MutableMap<Path, TestFramework> = HashMap<Path, TestFramework>()

    override fun createTestFramework(task: Test): TestFramework {
        return testFrameworks.computeIfAbsent(task.getIdentityPath()) { k: Path? -> delegate.createTestFramework(task) }
    }

    override fun getCompileOnlyDependencies(): Iterable<Dependency> {
        return delegate.getCompileOnlyDependencies()
    }

    override fun getRuntimeOnlyDependencies(): Iterable<Dependency> {
        return delegate.getRuntimeOnlyDependencies()
    }

    override fun getImplementationDependencies(): Iterable<Dependency> {
        return delegate.getImplementationDependencies()
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    override fun getParameters(): T? {
        return delegate.getParameters()
    }
}
