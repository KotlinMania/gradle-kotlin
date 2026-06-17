/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.cpp.internal

import com.google.common.collect.ImmutableSet
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.internal.component.ConfigurationSoftwareComponentVariant

class MainLibraryVariant(
    private val name: String,
    private val variant: NamedDomainObjectProvider<ConsumableConfiguration?>,
    private val attributeContainer: AttributeContainer,
    objectFactory: ObjectFactory
) : ComponentWithVariants, SoftwareComponentInternal {
    private val artifacts: MutableSet<PublishArtifact?> = LinkedHashSet<PublishArtifact?>()
    private val variants: DomainObjectSet<SoftwareComponent?>

    init {
        this.variants = objectFactory.domainObjectSet<SoftwareComponent?>(SoftwareComponent::class.java)
    }

    override fun getName(): String {
        return name
    }

    override fun getUsages(): MutableSet<out UsageContext?> {
        return ImmutableSet.of<ConfigurationSoftwareComponentVariant?>(ConfigurationSoftwareComponentVariant(name, attributeContainer, artifacts, variant.get()!!))
    }

    override fun getVariants(): MutableSet<out SoftwareComponent?> {
        return variants
    }

    fun addArtifact(artifact: PublishArtifact?) {
        artifacts.add(artifact)
    }

    /**
     * Adds a child variant
     */
    fun addVariant(variant: SoftwareComponent?) {
        variants.add(variant)
    }
}
