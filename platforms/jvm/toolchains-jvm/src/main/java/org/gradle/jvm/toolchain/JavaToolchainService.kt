/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.jvm.toolchain

import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Allows to query for toolchain managed tools, like [JavaCompiler], [JavaLauncher] and [JavadocTool].
 *
 *
 * An instance of this service is available for injection into tasks, plugins and other types.
 *
 * @since 6.7
 */
@ServiceScope(Scope.Project::class)
interface JavaToolchainService {
    /**
     * Obtain a [JavaCompiler] matching the [JavaToolchainSpec], as configured by the provided action.
     *
     * @param config The configuration of the `JavaToolchainSpec`
     * @return A `Provider<JavaCompiler>`
     */
    fun compilerFor(config: Action<in JavaToolchainSpec>): Provider<JavaCompiler>?

    /**
     * Obtain a [JavaCompiler] matching the [JavaToolchainSpec].
     *
     * @param spec The `JavaToolchainSpec`
     * @return A `Provider<JavaCompiler>`
     */
    fun compilerFor(spec: JavaToolchainSpec): Provider<JavaCompiler>?

    /**
     * Obtain a [JavaLauncher] matching the [JavaToolchainSpec], as configured by the provided action.
     *
     * @param config The configuration of the `JavaToolchainSpec`
     * @return A `Provider<JavaLauncher>`
     */
    fun launcherFor(config: Action<in JavaToolchainSpec>): Provider<JavaLauncher>?

    /**
     * Obtain a [JavaLauncher] matching the [JavaToolchainSpec].
     *
     * @param spec The `JavaToolchainSpec`
     * @return A `Provider<JavaLauncher>`
     */
    fun launcherFor(spec: JavaToolchainSpec): Provider<JavaLauncher>?

    /**
     * Obtain a [JavadocTool] matching the [JavaToolchainSpec], as configured by the provided action.
     *
     * @param config The configuration of the `JavaToolchainSpec`
     * @return A `Provider<JavadocTool>`
     */
    fun javadocToolFor(config: Action<in JavaToolchainSpec>): Provider<JavadocTool>?

    /**
     * Obtain a [JavadocTool] matching the [JavaToolchainSpec].
     *
     * @param spec The `JavaToolchainSpec`
     * @return A `Provider<JavadocTool>`
     */
    fun javadocToolFor(spec: JavaToolchainSpec): Provider<JavadocTool>?
}
