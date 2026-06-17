/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.tooling.events.test.TestOperationDescriptor

/**
 *
 *
 * A `TestLauncher` allows you to execute tests in a Gradle build.
 *
 *
 * If the target Gradle version is &gt;=6.8 then you can use `TestLauncher` to execute tests from included builds. Test operation descriptors from included builds work out-of-the-box. You can
 * target tasks from included builds by specifying the task identity path (i.e. `':included-build-name:subproject-name:taskName'`).
 *
 *
 * However, you cannot use the methods with included builds that don't specify the target test tasks (e.g. `withJvmTestClasses()` and `withJvmTestMethods()`). Those methods configure
 * all test tasks in the root build only.
 *
 * @since 2.6
 */
interface TestLauncher : ConfigurableLauncher<TestLauncher?> {
    /**
     * Adds tests to be executed by passing test descriptors received from a previous Gradle Run.
     *
     * @param descriptors The OperationDescriptor defining one or more tests.
     * @return this
     * @since 2.6
     */
    fun withTests(vararg descriptors: TestOperationDescriptor?): TestLauncher?

    /**
     * Adds tests to be executed by passing test descriptors received from a previous Gradle Run.
     *
     * @param descriptors The OperationDescriptor defining one or more tests.
     * @return this
     * @since 2.6
     */
    fun withTests(descriptors: Iterable<out TestOperationDescriptor?>?): TestLauncher?

    /**
     *
     * Adds tests to be executed declared by class name.
     *
     *
     * This method ignores tests defined in included builds.
     *
     * @param testClasses The class names of the tests to be executed.
     * @return this
     * @since 2.6
     */
    fun withJvmTestClasses(vararg testClasses: String?): TestLauncher?


    /**
     *
     * Adds tests to be executed declared by class name.
     *
     *
     * This method ignores tests defined in included builds.
     *
     * @param testClasses The class names of the tests to be executed.
     * @return this
     * @since 2.6
     */
    fun withJvmTestClasses(testClasses: Iterable<String?>?): TestLauncher?

    /**
     *
     * Adds tests to be executed declared by class and method name.
     *
     *
     * This method ignores tests defined in included builds.
     *
     * @param testClass The name of the class containing the methods to execute.
     * @param methods The names of the test methods to be executed.
     * @return this
     * @since 2.7
     */
    fun withJvmTestMethods(testClass: String?, vararg methods: String?): TestLauncher?

    /**
     *
     * Adds tests to be executed declared by class and methods name.
     *
     *
     * This method ignores tests defined in included builds.
     *
     * @param testClass The name of the class containing the methods to execute.
     * @param methods The names of the test methods to be executed.
     * @return this
     * @since 2.7
     */
    fun withJvmTestMethods(testClass: String?, methods: Iterable<String?>?): TestLauncher?

    /**
     * Adds tests to be executed declared by the container task and the class name.
     *
     *
     * Note: These tests are ignored for target Gradle version earlier than 6.1
     *
     * @param task The path of the target task.
     * @param testClasses The class names of the tests to be executed.
     * @return this
     * @since 6.1
     */
    fun withTaskAndTestClasses(task: String?, testClasses: Iterable<String?>?): TestLauncher?

    /**
     * Adds tests to be executed declared by the container task, class and method name.
     *
     *
     * Note: These tests are ignored for target Gradle version earlier than 6.1
     * @param task The path of the target task.
     * @param testClass The name of the class containing the methods to execute.
     * @param methods The names of the test methods to be executed.
     * @return this
     * @since 6.1
     */
    fun withTaskAndTestMethods(task: String?, testClass: String?, methods: Iterable<String?>?): TestLauncher?

    /**
     * Configures test JVM to run in debug mode.
     *
     *
     * When called, the forked test JVM is launched with the following argument:
     * <pre>-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=localhost:&lt;port&gt;</pre>
     * This means the test JVM expects a debugger at the specified port that uses a
     * [socket listening connector](https://docs.oracle.com/javase/6/docs/technotes/guides/jpda/conninv.html#Connectors).
     * If the debugger is not present then the test execution will fail.
     *
     *
     * Invoking this method adjusts the test task to launch only one JVM. More specifically, the parallel execution
     * gets disabled and the `forkEvery` property is set to 0.
     *
     * @param port the target port where the test JVM expects the debugger
     * @return this
     *
     * @since 5.6
     */
    fun debugTestsOn(port: Int): TestLauncher?

    /**
     * Sets the tasks to be executed. If no tasks are specified, the project's default tasks are executed.
     *
     *
     * The specified tasks are ignored if the target Gradle versions is &lt;7.6.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * @return this
     * @since 7.6
     */
    @Incubating
    fun forTasks(vararg tasks: String?): TestLauncher?

    /**
     * Executes the tests, blocking until complete.
     *
     * @throws TestExecutionException when one or more tests fail, or no tests for execution declared or no matching tests can be found.
     * @throws UnsupportedVersionException When the target Gradle version does not support test execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by [.withArguments].
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     * When the target Gradle version does not support some requested configuration option.
     * @throws BuildException On some failure while executing the tests in the Gradle build.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 2.6
     */
    @Throws(TestExecutionException::class)
    fun run()

    /**
     * Starts executing the tests. This method returns immediately, and the result is later passed to the given handler.
     *
     *
     * If the operation fails, the handler's [ResultHandler.onFailure]
     * method is called with the appropriate exception. See [.run] for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 2.6
     */
    fun run(handler: ResultHandler<in Void?>?)

    /**
     * Adds tests to be executed declared using a fine-grained test selection API.
     *
     *
     *
     * Clients can target tests via the `TestSpec` interface, which can configure the target test tasks as well as what tests should be executed
     * <pre>
     * TestLauncher testLauncher = projectConnection.newTestLauncher();
     * testLauncher.withTestsFor(spec -&gt; {
     * spec.forTaskPath(":test")
     * .includePackage("org.pkg")
     * .includeClass("com.TestClass")
     * .includeMethod("com.TestClass")
     * .includePattern("io.*")
     * }).run();
    </pre> *
     *
     *
     *
     * Note: These tests are ignored for target Gradle version earlier than 7.6.
     *
     * @param testSpec The action selecting the target tests.
     * @return this
     * @since 7.6
     */
    @Incubating
    fun withTestsFor(testSpec: Action<TestSpecs?>?): TestLauncher?
}
