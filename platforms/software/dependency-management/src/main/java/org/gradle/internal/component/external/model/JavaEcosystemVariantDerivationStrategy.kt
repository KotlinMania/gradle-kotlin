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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.repositories.metadata.MavenVariantAttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ImmutableModuleSources.Companion.of
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.MutableModuleSources.Companion.of
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import javax.inject.Inject

class JavaEcosystemVariantDerivationStrategy @Inject constructor(private val mavenAttributesFactory: MavenVariantAttributesFactory) : AbstractStatelessDerivationStrategy() {
    override fun derivesVariants(): Boolean {
        return true
    }

    override fun derive(metadata: ModuleComponentResolveMetadata?): ImmutableList<out ModuleConfigurationMetadata?>? {
        if (metadata is DefaultMavenModuleResolveMetadata) {
            val md = metadata
            val attributes = md.getAttributes()
            val compileConfiguration = md.getConfiguration("compile") as DefaultConfigurationMetadata?
            val runtimeConfiguration = md.getConfiguration("runtime") as DefaultConfigurationMetadata?
            val componentId = md.getId()
            val shadowedPlatformCapability = buildShadowPlatformCapability(componentId!!, false)
            val shadowedEnforcedPlatformCapability = buildShadowPlatformCapability(componentId, true)
            return ImmutableList.of<ModuleConfigurationMetadata?>( // When deriving variants for the Java ecosystem, we actually have 2 components "mixed together": the library and the platform
                // and there's no way to figure out what was the intent when it was published. So we derive variants for both.
                libraryCompileScope(compileConfiguration!!, attributes),
                libraryRuntimeScope(runtimeConfiguration!!, attributes),
                libraryWithSourcesVariant(runtimeConfiguration, attributes, metadata),
                libraryWithJavadocVariant(runtimeConfiguration, attributes, metadata),
                platformWithUsageAttribute(compileConfiguration, attributes, Usage.JAVA_API, false, shadowedPlatformCapability),
                platformWithUsageAttribute(runtimeConfiguration, attributes, Usage.JAVA_RUNTIME, false, shadowedPlatformCapability),
                platformWithUsageAttribute(compileConfiguration, attributes, Usage.JAVA_API, true, shadowedEnforcedPlatformCapability),
                platformWithUsageAttribute(runtimeConfiguration, attributes, Usage.JAVA_RUNTIME, true, shadowedEnforcedPlatformCapability)
            )
        }
        return null
    }

    /**
     * Synthesizes a "sources" variant since maven metadata cannot represent it
     *
     * @return synthetic metadata for the sources-classifier jar
     */
    private fun libraryWithSourcesVariant(
        runtimeConfiguration: DefaultConfigurationMetadata,
        originAttributes: ImmutableAttributes,
        metadata: ModuleComponentResolveMetadata
    ): DefaultConfigurationMetadata? {
        return runtimeConfiguration.mutate()
            .withName("sources")
            .withAttributes(mavenAttributesFactory.sourcesVariant(originAttributes))
            .withArtifacts(ImmutableList.of<ModuleComponentArtifactMetadata?>(metadata.optionalArtifact("jar", "jar", "sources")))
            .withoutConstraints()
            .build()
    }

    /**
     * Synthesizes a "javadoc" variant since maven metadata cannot represent it
     *
     * @return synthetic metadata for the javadoc-classifier jar
     */
    private fun libraryWithJavadocVariant(
        runtimeConfiguration: DefaultConfigurationMetadata,
        originAttributes: ImmutableAttributes,
        metadata: ModuleComponentResolveMetadata
    ): ModuleConfigurationMetadata? {
        return runtimeConfiguration.mutate()
            .withName("javadoc")
            .withAttributes(mavenAttributesFactory.javadocVariant(originAttributes))
            .withArtifacts(ImmutableList.of<ModuleComponentArtifactMetadata?>(metadata.optionalArtifact("jar", "jar", "javadoc")))
            .withoutConstraints()
            .build()
    }

    private fun buildShadowPlatformCapability(componentId: ModuleComponentIdentifier, enforced: Boolean): ImmutableCapabilities {
        return ImmutableCapabilities.Companion.of(
            ShadowedImmutableCapability(
                DefaultImmutableCapability(
                    componentId.getGroup(),
                    componentId.getModule(),
                    componentId.getVersion()
                ), if (enforced) "-derived-enforced-platform" else "-derived-platform"
            )
        )
    }

    private fun libraryCompileScope(conf: DefaultConfigurationMetadata, originAttributes: ImmutableAttributes): ModuleConfigurationMetadata? {
        val attributes = mavenAttributesFactory.compileScope(originAttributes)
        return conf.mutate()
            .withAttributes(attributes)
            .withoutConstraints()
            .build()
    }

    private fun libraryRuntimeScope(conf: DefaultConfigurationMetadata, originAttributes: ImmutableAttributes): ModuleConfigurationMetadata? {
        val attributes = mavenAttributesFactory.runtimeScope(originAttributes)
        return conf.mutate()
            .withAttributes(attributes)
            .withoutConstraints()
            .build()
    }

    private fun platformWithUsageAttribute(
        conf: DefaultConfigurationMetadata,
        originAttributes: ImmutableAttributes,
        usage: String,
        enforcedPlatform: Boolean,
        shadowedPlatformCapability: ImmutableCapabilities?
    ): ModuleConfigurationMetadata? {
        val attributes = mavenAttributesFactory.platformWithUsage(originAttributes, usage, enforcedPlatform)
        val prefix = if (enforcedPlatform) "enforced-platform-" else "platform-"
        var builder = conf.mutate()
            .withName(prefix + conf.getName())
            .withAttributes(attributes)
            .withConstraintsOnly()
            .withCapabilities(shadowedPlatformCapability)
        if (enforcedPlatform) {
            builder = builder.withForcedDependencies()
        }
        return builder.build()
    }
}
