/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour

class DefaultJavaFeatureSpec(private val name: String, private val project: ProjectInternal) : FeatureSpec {
    private val capabilities: MutableSet<Capability?> = LinkedHashSet<Capability?>(1)

    private var sourceSet: SourceSet? = null
    private var withJavadocJar = false
    private var withSourcesJar = false
    var isPublished: Boolean = true
        private set

    override fun usingSourceSet(sourceSet: SourceSet) {
        this.sourceSet = sourceSet
    }

    override fun capability(group: String, name: String, version: String?) {
        capabilities.add(DefaultImmutableCapability(group, name, version))
    }

    override fun withJavadocJar() {
        withJavadocJar = true
    }

    override fun withSourcesJar() {
        withSourcesJar = true
    }

    override fun disablePublication() {
        this.isPublished = false
    }

    fun create(): JvmFeatureInternal {
        if (sourceSet == null) {
            throw InvalidUserCodeException("You must specify which source set to use for feature '" + name + "'")
        }

        if (capabilities.isEmpty()) {
            capabilities.add(ProjectDerivedCapability(project, name))
        }

        if (SourceSet.isMain(sourceSet!!)) {
            deprecateBehaviour(String.format("The '%s' feature was created using the main source set.", name))
                .withAdvice("The main source set is reserved for production code and should not be used for features. Use another source set instead.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(8, "deprecate_register_feature_main_source_set")!!
                .nagUser()
        }

        val feature: JvmFeatureInternal = DefaultJvmFeature(name, sourceSet!!, capabilities, project, SourceSet.isMain(sourceSet!!))
        feature.withApi()
        return feature
    }

    /**
     * Return true if [.withJavadocJar] was called.
     */
    fun hasJavadocJar(): Boolean {
        return withJavadocJar
    }

    /**
     * Return true if [.withSourcesJar] was called.
     */
    fun hasSourcesJar(): Boolean {
        return withSourcesJar
    }
}
