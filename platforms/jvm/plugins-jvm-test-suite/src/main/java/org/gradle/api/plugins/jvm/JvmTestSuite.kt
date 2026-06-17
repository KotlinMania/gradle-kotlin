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

import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Incubating
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.SourceSet
import org.gradle.testing.base.TestSuite

/**
 * A test suite is a collection of JVM-based tests.
 *
 *
 * Each test suite consists of
 *
 *  * A [SourceSet]
 *  * A set of [compile and runtime dependencies][JvmComponentDependencies]
 *  * One or more [targets][JvmTestSuiteTarget]
 *  * A testing framework
 *
 *
 *
 * Based on the testing framework declared, Gradle will automatically add the appropriate dependencies and configure the underlying test task.
 *
 *
 * The default test suite (named [JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME]) will default to using the
 * JUnit 4 test framework for backwards compatibility.  Any other test suite will default to using the JUnit Jupiter test framework.
 *
 * @since 7.3
 */
@Incubating
interface JvmTestSuite : TestSuite, Buildable {
    // TODO: Rename to getSourceSet next time changes are made in this area.
    /**
     * Returns the container of [JvmTestSuiteTarget] objects part of this suite.
     *
     * Source set associated with this test suite. The name of this source set is the same as the test suite.
     *
     * @return source set for this test suite.
     */
    @JvmField
    val sources: SourceSet?

    /**
     * Configure the sources for this test suite.
     *
     * @param configuration configuration applied against the SourceSet for this test suite
     */
    fun sources(configuration: Action<in SourceSet>)

    /**
     * Collection of test suite targets.
     *
     * Each test suite target executes the tests in this test suite with a particular context and task.
     *
     * @return collection of test suite targets.
     */
    override fun getTargets(): ExtensiblePolymorphicDomainObjectContainer<out JvmTestSuiteTarget>?

    /**
     * Use the [JUnit Jupiter](https://junit.org/junit5/docs/current/user-guide/) testing framework.
     *
     *
     *
     * Gradle will provide the version of JUnit Jupiter to use. Defaults to version `5.8.2`
     *
     */
    fun useJUnitJupiter()

    /**
     * Use the [JUnit Jupiter](https://junit.org/junit5/docs/current/user-guide/) testing framework with a specific version.
     *
     * @param version version of JUnit Jupiter to use
     */
    fun useJUnitJupiter(version: String)

    /**
     * Use the [JUnit Jupiter](https://junit.org/junit5/docs/current/user-guide/) testing framework with a specific version.
     *
     * @param version provider supplying the version of JUnit Jupiter to use
     *
     * @since 7.6
     */
    fun useJUnitJupiter(version: Provider<String>)

    /**
     * Use the [JUnit4](https://junit.org/junit4/) testing framework.
     *
     *
     * Gradle will provide the version of JUnit4 to use. Defaults to version `4.13.2`
     *
     */
    fun useJUnit()

    /**
     * Use the [JUnit4](https://junit.org/junit4/) testing framework with a specific version.
     *
     * @param version version of JUnit4 to use
     */
    fun useJUnit(version: String)

    /**
     * Use the [JUnit4](https://junit.org/junit4/) testing framework with a specific version.
     *
     * @param version provider supplying the version of JUnit4 to use
     *
     * @since 7.6
     */
    fun useJUnit(version: Provider<String>)

    /**
     * Use the [Spock Framework](https://spockframework.org/) testing framework.
     *
     *
     * Gradle will provide the version of Spock to use. Defaults to version `2.3-groovy-4.0`
     *
     */
    fun useSpock()

    /**
     * Use the [Spock Framework](https://spockframework.org/) testing framework with a specific version.
     *
     * @param version the version of Spock to use
     */
    fun useSpock(version: String)

    /**
     * Use the [Spock Framework](https://spockframework.org/) testing framework with a specific version.
     *
     * @param version provider supplying the version of Spock to use
     *
     * @since 7.6
     */
    fun useSpock(version: Provider<String>)

    /**
     * Use the [kotlin.test](https://kotlinlang.org/api/latest/kotlin.test/) testing framework.
     *
     *
     * Gradle will provide the version of kotlin.test to use. Defaults to version `1.6.20`
     *
     */
    fun useKotlinTest()

    /**
     * Use the [kotlin.test](https://kotlinlang.org/api/latest/kotlin.test/) testing framework with a specific version.
     *
     * @param version the version of kotlin.test to use
     */
    fun useKotlinTest(version: String)

    /**
     * Use the [kotlin.test](https://kotlinlang.org/api/latest/kotlin.test/) testing framework with a specific version.
     *
     * @param version provider supplying the version of kotlin.test to use
     *
     * @since 7.6
     */
    fun useKotlinTest(version: Provider<String>)

    /**
     * Use the [TestNG](https://testng.org/doc/) testing framework.
     *
     *
     * Gradle will provide the version of TestNG to use. Defaults to version `7.4.0`
     *
     */
    fun useTestNG()

    /**
     * Use the [TestNG](https://testng.org/doc/) testing framework with a specific version.
     *
     * @param version version of TestNG to use
     */
    fun useTestNG(version: String)

    /**
     * Use the [TestNG](https://testng.org/doc/) testing framework with a specific version.
     *
     * @param version provider supplying the version of TestNG to use
     *
     * @since 7.6
     */
    fun useTestNG(version: Provider<String>)

    @get:Nested
    val dependencies: JvmComponentDependencies?

    /**
     * Configure dependencies for this component.
     */
    fun dependencies(dependencies: Action<in JvmComponentDependencies>)
}
