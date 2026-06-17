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
package org.gradle.api.plugins.jvm.internal

/**
 * Allows configuration of attributes used for JVM related components.
 * This can be used both on the producer side, to explain what it provides,
 * or on the consumer side, to express requirements.
 */
interface JvmEcosystemAttributesDetails {
    /**
     * Provides or requires a library
     */
    fun library(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a library with specific elements.
     * See [org.gradle.api.attributes.LibraryElements] for possible values.
     */
    fun library(elementsType: String?): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a platform
     */
    fun platform(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires documentation
     * @param docsType the documentation type (javadoc, sources, ...)
     */
    fun documentation(docsType: String?): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires an API
     */
    fun apiUsage(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a runtime
     */
    fun runtimeUsage(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a component which dependencies are found
     * as independent components (typically through external dependencies)
     */
    fun withExternalDependencies(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a component which dependencies are bundled as part
     * of the main artifact
     */
    fun withEmbeddedDependencies(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a component which dependencies are bundled as part
     * of the main artifact in a relocated/shadowed form
     */
    fun withShadowedDependencies(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires a complete component (jar) and not just the classes or
     * resources
     */
    fun asJar(): JvmEcosystemAttributesDetails?

    /**
     * Configures the target JVM version. For producers of a library, it's in general
     * a better idea to rely on inference which will calculate the target JVM version
     * lazily, for example calling `JvmLanguageUtilities#useDefaultTargetPlatformInference(Configuration, TaskProvider)`.
     * For consumers, it makes sense to specify a specific version of JVM they target.
     *
     * @param version the Java version
     */
    fun withTargetJvmVersion(version: Int): JvmEcosystemAttributesDetails?

    /**
     * Expresses that variants which are optimized for standard JVMs should be preferred.
     */
    fun preferStandardJVM(): JvmEcosystemAttributesDetails?

    /**
     * Provides or requires the source files of a component.
     *
     * @return `this`
     */
    fun asSources(): JvmEcosystemAttributesDetails?
}
