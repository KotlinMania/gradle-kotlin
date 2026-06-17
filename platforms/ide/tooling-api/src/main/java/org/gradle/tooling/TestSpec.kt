/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.tooling

import org.gradle.api.Incubating

/**
 * Provides infrastructure to select which test classes, methods, and packages will be included in the test execution.
 *
 *
 * A complex example:
 * <pre>
 * TestLauncher testLauncher = projectConnection.newTestLauncher();
 * testLauncher.withTestsFor(specs -&gt; {
 * specs.forTaskPath(":test")                                    // configure the test selection on the ':test' task
 * .includePackage("org.pkg")                               // execute all tests declared the org.pkg package and its sub-packages
 * .includeClass("com.MyTest")                              // execute the MyTest test class
 * .includeMethod("com.OtherTest", Arrays.asList("verify")) // execute the OtherTest.verify() test method
 * .includePattern("io.*")                                  // execute all tests matching to io.*
 * }).run();
</pre> *
 *
 *
 * All methods on this interface (including the class and method selection) support patterns as arguments. The patterns follow the rules of
 * [test filtering](https://docs.gradle.org/current/userguide/java_testing.html#test_filtering).
 *
 *
 * The test execution will fail if any of the selected classes, methods, or patters have no matching tests.
 *
 * @since 7.6
 */
@Incubating
interface TestSpec {
    /**
     * Adds all tests declared in the target package to the test execution.
     *
     *
     * The effect is recursive, meaning that the tests defined in sub-packages will also be executed.
     *
     * @param pkg The target package.
     * @return this
     */
    fun includePackage(pkg: String?): TestSpec?

    /**
     * Adds all tests declared in the target packages to the test execution.
     *
     * @see .includePackage
     * @param packages The target packages.
     * @return this
     */
    fun includePackages(packages: MutableCollection<String?>?): TestSpec?

    /**
     * Adds the target test class to the test execution.
     *
     *
     * The target class should be selected with its fully-qualified name.
     *
     * @param cls The fully-qualified name of the target class.
     * @return this
     */
    fun includeClass(cls: String?): TestSpec?

    /**
     * Adds the target test classes to the test execution.
     *
     * @see .includeClass
     * @param classes The fully-qualified name of the target classes.
     * @return this
     */
    fun includeClasses(classes: MutableCollection<String?>?): TestSpec?

    /**
     * Adds the target test method to the test execution.
     *
     *
     * The target method should be selected with its fully-qualified class name and with the name of the method.
     *
     * @param cls The fully-qualified name of the class containing the method.
     * @param method The name of the target method.
     * @return this
     */
    fun includeMethod(cls: String?, method: String?): TestSpec?

    /**
     * Adds the target test methods to the test execution.
     *
     * @see .includeMethod
     * @param cls The fully-qualified name of the class containing the method.
     * @param methods The name of the target methods.
     * @return this
     */
    fun includeMethods(cls: String?, methods: MutableCollection<String?>?): TestSpec?

    /**
     * Adds all tests to the execution that matches the target pattern.
     *
     *
     * The patterns follow the rules of
     * [test filtering](https://docs.gradle.org/current/userguide/java_testing.html#test_filtering).
     *
     * @param pattern the pattern to select tests.
     * @return this
     */
    fun includePattern(pattern: String?): TestSpec?

    /**
     * Adds all tests to the execution that matches the target patterns.
     *
     * @see .includePattern
     * @param patterns the patterns to select tests.
     * @return this
     */
    fun includePatterns(patterns: MutableCollection<String?>?): TestSpec?
}
