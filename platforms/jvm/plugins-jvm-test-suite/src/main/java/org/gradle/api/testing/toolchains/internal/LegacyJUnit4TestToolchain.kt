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

import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

/**
 * A [JvmTestToolchain] that uses JUnit 4 with legacy behavior for the default test suite.  Specifically,
 * it does not provide any dependencies for compiling or executing tests.  Instead, these should be provided by
 * the user.
 *
 * @since 8.5
 */
abstract class LegacyJUnit4TestToolchain : JvmTestToolchain<JvmTestToolchainParameters.None> {
    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    override fun createTestFramework(task: Test): TestFramework {
        return this.objectFactory.newInstance<JUnitTestFramework>(JUnitTestFramework::class.java, task.getFilter(), task.getTemporaryDirFactory(), task.dryRun)
    }
}
