/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.plugins.jvm

import org.gradle.api.Incubating
import org.gradle.api.artifacts.dsl.GradleDependencies

/**
 * This DSL element is used to add dependencies to a component, for instance a [TestSuite]
 *
 *
 *  * `implementation` dependencies are used at compilation and runtime.
 *  * `compileOnly` dependencies are used only at compilation and are not available at runtime.
 *  * `runtimeOnly` dependencies are not available at compilation and are used only at runtime.
 *  * `annotationProcessor` dependencies are used only at compilation for the annotation processor classpath
 *
 *
 * @apiNote This interface combines various [Dependencies] APIs into a DSL type that can be used to add dependencies for JVM components.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler For more information.
 *
 * @since 7.3
 */
@Incubating
interface JvmComponentDependencies : PlatformDependencyModifiers, TestFixturesDependencyModifiers, GradleDependencies {
    /**
     * Returns a [DependencyCollector] that collects the set of implementation dependencies.
     *
     *
     * `implementation` dependencies are used at compilation and runtime.
     *
     * @return a [DependencyCollector] that collects the set of implementation dependencies
     * @since 7.6
     */
    @JvmField
    val implementation: DependencyCollector?

    /**
     * Returns a [DependencyCollector] that collects the set of compile-only dependencies.
     *
     *
     * `compileOnly` dependencies are used only at compilation and are not available at runtime.
     *
     * @return a [DependencyCollector] that collects the set of compile-only dependencies
     * @since 7.6
     */
    @JvmField
    val compileOnly: DependencyCollector?

    /**
     * Returns a [DependencyCollector] that collects the set of runtime-only dependencies.
     *
     *
     * `runtimeOnly` dependencies are not available at compilation and are used only at runtime.
     *
     * @return a [DependencyCollector] that collects the set of runtime-only dependencies
     * @since 7.6
     */
    @JvmField
    val runtimeOnly: DependencyCollector?

    /**
     * Returns a [DependencyCollector] that collects the set of annotation processor dependencies.
     *
     *
     * `annotationProcessor` dependencies are used only at compilation, and are added to the annotation processor classpath.
     *
     * @return a [DependencyCollector] that collects the of annotation processor dependencies
     * @since 7.6
     */
    @JvmField
    val annotationProcessor: DependencyCollector?
}
