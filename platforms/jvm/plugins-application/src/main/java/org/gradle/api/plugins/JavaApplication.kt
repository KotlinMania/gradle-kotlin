/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * Configuration for a Java application, defining how to assemble the application.
 *
 *
 * An instance of this type is added as a project extension by the Java application plugin
 * under the name 'application'.
 *
 * <pre class='autoTested'>
 * plugins {
 * id 'application'
 * }
 *
 * application {
 * mainClass.set("com.foo.bar.FooBar")
 * }
</pre> *
 *
 * @since 4.10
 */
interface JavaApplication {
    /**
     * The name of the application.
     */
    @get:ToBeReplacedByLazyProperty
    var applicationName: String?

    /**
     * The name of the application's Java module if it should run as a module.
     *
     * @since 6.4
     */
    val mainModule: Property<String>?

    /**
     * The fully qualified name of the application's main class.
     *
     * @since 6.4
     */
    val mainClass: Property<String>?

    /**
     * Array of string arguments to pass to the JVM when running the application
     */
    @get:ToBeReplacedByLazyProperty
    var applicationDefaultJvmArgs: Iterable<String>?

    /**
     * Directory to place executables in
     */
    @get:ToBeReplacedByLazyProperty
    var executableDir: String?

    @get:NotToBeReplacedByLazyProperty(because = "Read-only nested like property")
    var applicationDistribution: CopySpec?
}
