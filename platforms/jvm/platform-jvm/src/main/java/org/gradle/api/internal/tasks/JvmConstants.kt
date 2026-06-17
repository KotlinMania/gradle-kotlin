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
package org.gradle.api.internal.tasks

/**
 * Common constants used across the JVM ecosystem. Many of these constants define the configuration and task
 * names added by the Java plugin. Eventually, we should deprecate many of the public constants in the Java plugin
 * and push users to using the corresponding methods on [org.gradle.api.tasks.SourceSet].
 */
object JvmConstants {
    /**
     * The name of the task that processes resources.
     */
    const val PROCESS_RESOURCES_TASK_NAME: String = "processResources"

    /**
     * The name of the lifecycle task which outcome is that all the classes of a component are generated.
     */
    const val CLASSES_TASK_NAME: String = "classes"

    /**
     * The name of the task which compiles Java sources.
     */
    const val COMPILE_JAVA_TASK_NAME: String = "compileJava"

    /**
     * The name of the task which processes the test resources.
     */
    const val PROCESS_TEST_RESOURCES_TASK_NAME: String = "processTestResources"

    /**
     * The name of the lifecycle task which outcome is that all test classes of a component are generated.
     */
    const val TEST_CLASSES_TASK_NAME: String = "testClasses"

    /**
     * The name of the task which compiles the test Java sources.
     */
    const val COMPILE_TEST_JAVA_TASK_NAME: String = "compileTestJava"

    /**
     * The name of the task which triggers execution of tests.
     */
    const val TEST_TASK_NAME: String = "test"

    /**
     * The name of the task which generates the component main jar.
     */
    const val JAR_TASK_NAME: String = "jar"

    /**
     * The name of the task which generates the component javadoc.
     */
    const val JAVADOC_TASK_NAME: String = "javadoc"

    /**
     * The name of the API configuration, where dependencies exported by a component at compile time should
     * be declared.
     *
     * @since 3.4
     */
    const val API_CONFIGURATION_NAME: String = "api"

    /**
     * The name of the implementation configuration, where dependencies that are only used internally by
     * a component should be declared.
     *
     * @since 3.4
     */
    const val IMPLEMENTATION_CONFIGURATION_NAME: String = "implementation"

    /**
     * The name of the configuration to define the API elements of a component.
     * That is, the dependencies which are required to compile against that component.
     *
     * @since 3.4
     */
    const val API_ELEMENTS_CONFIGURATION_NAME: String = "apiElements"

    /**
     * The name of the configuration that is used to declare dependencies which are only required to compile a component,
     * but not at runtime.
     */
    const val COMPILE_ONLY_CONFIGURATION_NAME: String = "compileOnly"

    /**
     * The name of the configuration to define the API elements of a component that are required to compile a component,
     * but not at runtime.
     *
     * @since 6.7
     */
    const val COMPILE_ONLY_API_CONFIGURATION_NAME: String = "compileOnlyApi"

    /**
     * The name of the runtime only dependencies configuration, used to declare dependencies
     * that should only be found at runtime.
     *
     * @since 3.4
     */
    const val RUNTIME_ONLY_CONFIGURATION_NAME: String = "runtimeOnly"

    /**
     * The name of the runtime classpath configuration, used by a component to query its own runtime classpath.
     *
     * @since 3.4
     */
    const val RUNTIME_CLASSPATH_CONFIGURATION_NAME: String = "runtimeClasspath"

    /**
     * The name of the runtime elements configuration, that should be used by consumers
     * to query the runtime dependencies of a component.
     *
     * @since 3.4
     */
    const val RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "runtimeElements"

    /**
     * The name of the javadoc elements configuration.
     *
     * @since 6.0
     */
    const val JAVADOC_ELEMENTS_CONFIGURATION_NAME: String = "javadocElements"

    /**
     * The name of the sources elements configuration.
     *
     * @since 6.0
     */
    const val SOURCES_ELEMENTS_CONFIGURATION_NAME: String = "sourcesElements"

    /**
     * The name of the compile classpath configuration.
     *
     * @since 3.4
     */
    const val COMPILE_CLASSPATH_CONFIGURATION_NAME: String = "compileClasspath"

    /**
     * The name of the annotation processor configuration.
     *
     * @since 4.6
     */
    const val ANNOTATION_PROCESSOR_CONFIGURATION_NAME: String = "annotationProcessor"

    /**
     * The name of the test implementation dependencies configuration.
     *
     * @since 3.4
     */
    const val TEST_IMPLEMENTATION_CONFIGURATION_NAME: String = "testImplementation"

    /**
     * The name of the configuration that should be used to declare dependencies which are only required
     * to compile the tests, but not when running them.
     */
    const val TEST_COMPILE_ONLY_CONFIGURATION_NAME: String = "testCompileOnly"

    /**
     * The name of the test runtime only dependencies configuration.
     *
     * @since 3.4
     */
    const val TEST_RUNTIME_ONLY_CONFIGURATION_NAME: String = "testRuntimeOnly"

    /**
     * The name of the test compile classpath configuration.
     *
     * @since 3.4
     */
    const val TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME: String = "testCompileClasspath"

    /**
     * The name of the test annotation processor configuration.
     *
     * @since 4.6
     */
    const val TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME: String = "testAnnotationProcessor"

    /**
     * The name of the test runtime classpath configuration.
     *
     * @since 3.4
     */
    const val TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME: String = "testRuntimeClasspath"

    /**
     * The name of the component added by the Java plugin.
     */
    const val JAVA_MAIN_COMPONENT_NAME: String = "java"

    /**
     * The name of the main feature added to the java component by the Java plugin.
     */
    const val JAVA_MAIN_FEATURE_NAME: String = "main"

    /**
     * Task group name for documentation-related tasks.
     */
    const val DOCUMENTATION_GROUP: String = "documentation"
}
