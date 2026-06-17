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

/**
 * A [JUnitPlatformTestToolchain] that uses the JUnit Jupiter test engine.
 *
 * @since 8.5
 */
abstract class JUnitJupiterTestToolchain : JUnitPlatformTestToolchain<JUnitJupiterToolchainParameters>() {
    override fun getImplementationDependencies(): Iterable<Dependency> {
        return mutableListOf<Dependency>(getDependencyFactory().create(GROUP_NAME + ":" + getParameters().getJupiterVersion().get()))
    }

    companion object {
        /**
         * The default version of JUnit Jupiter to use for compiling and executing tests.
         */
        const val DEFAULT_VERSION: String = "5.12.2"
        private const val GROUP_NAME = "org.junit.jupiter:junit-jupiter"
    }
}
