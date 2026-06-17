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
package org.gradle.jvm.component.internal

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.publish.internal.component.DefaultAdhocSoftwareComponent
import javax.inject.Inject

/**
 * A component with a set of features. Each feature is responsible for compiling, executing, packaging, etc a software
 * product such as a library or application. This component owns all features it contains and therefore transitively owns their
 * corresponding source sets and any domain objects which are created by the [BasePlugin] on the source sets' behalf.
 * This includes their resolvable configurations and dependency scopes, as well as any associated tasks.
 *
 * TODO: We should strip almost all logic from this class. It should be a simple container for features and should provide
 * a means of querying all variants of all features.
 */
abstract class DefaultJvmSoftwareComponent @Inject constructor(
    componentName: String,
    objectFactory: ObjectFactory,
    private val configurations: ConfigurationContainer
) : DefaultAdhocSoftwareComponent(componentName, objectFactory), JvmSoftwareComponentInternal {
    // TODO: The component itself should not be concerned with configuring the sources and javadoc jars
    // of its features. It should lazily react to the variants of the feature being added and configure
    // itself to in turn advertise those variants. However, this requires a more complete variant API,
    // which is still being designed. For now, we'll add the variants manually.
    override fun withJavadocJar() {
        // TODO: This should probably apply to all features and not just the main feature or this
        // should be configurable at the feature level instead of the project level.
        // The original implementation only applied to the main feature.
        features.all({ feature ->
            if (feature.getName().equals(JvmConstants.JAVA_MAIN_FEATURE_NAME)) {
                val javadocElements: NamedDomainObjectProvider<ConsumableConfiguration?> = feature.maybeRegisterJavadocElements()
                addVariantsFromConfiguration(javadocElements, JavaConfigurationVariantMapping("runtime", true))
            }
        })
    }

    override fun withSourcesJar() {
        // TODO: This should probably apply to all features and not just the main feature or this
        // should be configurable at the feature level instead of the project level.
        // The original implementation only applied to the main feature.
        features.all({ feature ->
            if (feature.getName().equals(JvmConstants.JAVA_MAIN_FEATURE_NAME)) {
                val sourcesElements: NamedDomainObjectProvider<ConsumableConfiguration?> = feature.maybeRegisterSourcesElements()
                addVariantsFromConfiguration(sourcesElements, JavaConfigurationVariantMapping("runtime", true))
            }
        })
    }

    val mainFeature: JvmFeatureInternal
        get() {
            val mainFeature: JvmFeatureInternal = features.findByName(JvmConstants.JAVA_MAIN_FEATURE_NAME)
            checkNotNull(mainFeature) { "Expected to find a feature named '" + JvmConstants.JAVA_MAIN_FEATURE_NAME + "' but found none." }

            return mainFeature
        }

    override fun useCompileClasspathConsistency() {
        testSuites.withType(JvmTestSuite::class.java).configureEach({ testSuite ->
            configurations.getByName(testSuite.getSources().getCompileClasspathConfigurationName())
                .shouldResolveConsistentlyWith(this.mainFeature.compileClasspathConfiguration)
        })
    }

    override fun useRuntimeClasspathConsistency() {
        testSuites.withType(JvmTestSuite::class.java).configureEach({ testSuite ->
            configurations.getByName(testSuite.getSources().getRuntimeClasspathConfigurationName())
                .shouldResolveConsistentlyWith(this.mainFeature.runtimeClasspathConfiguration)
        })
    }
}
