/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentConfigurationIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ImmutableModuleSources.Companion.of
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.internal.component.model.MutableModuleSources.Companion.of
import org.gradle.internal.component.model.VariantResolveMetadata

abstract class AbstractMutableModuleComponentResolveMetadata : MutableModuleComponentResolveMetadata {
    @JvmField
    val attributesFactory: AttributesFactory

    private var componentId: ModuleComponentIdentifier?
    var moduleVersionId: ModuleVersionIdentifier
        private set
    var isChanging: Boolean = false
    var isMissing: Boolean = false
    var isExternalVariant: Boolean = false
    var isComponentMetadataRuleCachingEnabled: Boolean
    var statusScheme: MutableList<String?>? = ExternalComponentResolveMetadata.Companion.DEFAULT_STATUS_SCHEME
    private var moduleSources: MutableModuleSources
    private /*Mutable*/ var componentLevelAttributes: AttributeContainerInternal
    val attributesSchema: ImmutableAttributesSchema?

    val variantMetadataRules: VariantMetadataRules
    val variantDerivationStrategy: VariantDerivationStrategy?

    private var newVariants: MutableList<MutableComponentVariant>? = null
    private var variants: ImmutableList<out ComponentVariant?>? = null
    private var owners: MutableSet<VirtualComponentIdentifier?>? = null

    protected constructor(
        attributesFactory: AttributesFactory,
        moduleVersionId: ModuleVersionIdentifier,
        componentIdentifier: ModuleComponentIdentifier?,
        schema: ImmutableAttributesSchema?
    ) {
        this.attributesFactory = attributesFactory
        this.componentId = componentIdentifier
        this.moduleVersionId = moduleVersionId
        this.componentLevelAttributes = defaultAttributes(attributesFactory)
        this.attributesSchema = schema
        this.variantMetadataRules = VariantMetadataRules(attributesFactory, moduleVersionId)
        this.moduleSources = MutableModuleSources()
        this.variantDerivationStrategy = NoOpDerivationStrategy.getInstance()
        this.isComponentMetadataRuleCachingEnabled = true
    }

    protected constructor(metadata: ModuleComponentResolveMetadata) {
        this.componentId = metadata.getId()
        this.moduleVersionId = metadata.getModuleVersionId()!!
        this.isChanging = metadata.isChanging()
        this.isMissing = metadata.isMissing()
        this.statusScheme = metadata.getStatusScheme()
        this.moduleSources = MutableModuleSources.of(metadata.getSources())
        this.variants = metadata.variants
        this.attributesFactory = metadata.attributesFactory
        this.attributesSchema = metadata.getAttributesSchema()
        this.componentLevelAttributes = attributesFactory.mutable(metadata.getAttributes())
        this.variantDerivationStrategy = metadata.variantDerivationStrategy
        this.variantMetadataRules = VariantMetadataRules(attributesFactory, moduleVersionId)
        this.isExternalVariant = metadata.isExternalVariant
        this.isComponentMetadataRuleCachingEnabled = metadata.isComponentMetadataRuleCachingEnabled
    }

    var id: ModuleComponentIdentifier
        get() = componentId
        set(componentId) {
            this.componentId = componentId
            this.moduleVersionId = DefaultModuleVersionIdentifier.newId(componentId)
        }

    var status: String?
        get() = componentLevelAttributes.getAttribute<String?>(ProjectInternal.STATUS_ATTRIBUTE)
        set(status) {
            val attributes = this.componentLevelAttributes
            attributes.attribute<String?>(ProjectInternal.STATUS_ATTRIBUTE, status)
            componentLevelAttributes = attributes
        }

    abstract val configurationDefinitions: ImmutableMap<String?, Configuration?>?

    var sources: MutableModuleSources
        get() = moduleSources
        set(sources) {
            this.moduleSources = MutableModuleSources.of(sources)
        }

    var attributes: AttributeContainer
        get() = componentLevelAttributes
        set(attributes) {
            this.componentLevelAttributes = attributesFactory.mutable(attributes as AttributeContainerInternal?)
            // the "status" attribute is mandatory, so if it's missing, we need to add it
            if (!attributes.contains(ProjectInternal.STATUS_ATTRIBUTE)) {
                componentLevelAttributes.attribute<String?>(
                    ProjectInternal.STATUS_ATTRIBUTE,
                    DEFAULT_STATUS
                )
            }
        }

    override fun artifact(type: String, extension: String?, classifier: String?): ModuleComponentArtifactMetadata? {
        val ivyArtifactName: IvyArtifactName = DefaultIvyArtifactName(moduleVersionId.getName(), type, extension, classifier)
        return DefaultModuleComponentArtifactMetadata(id, ivyArtifactName)
    }

    override fun addVariant(variantName: String?, attributes: ImmutableAttributes?): MutableComponentVariant? {
        return addVariant(MutableVariantImpl(variantName, attributes))
    }

    override fun addVariant(variant: MutableComponentVariant?): MutableComponentVariant? {
        if (newVariants == null) {
            newVariants = ArrayList<MutableComponentVariant>()
        }
        newVariants!!.add(variant!!)
        return variant
    }

    fun getVariants(): ImmutableList<out ComponentVariant?> {
        if (variants == null && newVariants == null) {
            return ImmutableList.of<ComponentVariant?>()
        }
        if (variants != null && newVariants == null) {
            return variants!!
        }
        val builder = ImmutableList.Builder<ComponentVariant?>()
        if (variants != null) {
            builder.addAll(variants!!)
        }
        for (variant in newVariants!!) {
            builder.add(
                AbstractMutableModuleComponentResolveMetadata.ImmutableVariantImpl(
                    id,
                    variant.name!!,
                    variant.attributes,
                    ImmutableList.copyOf<ComponentVariant.Dependency>(variant.dependencies),
                    ImmutableList.copyOf<ComponentVariant.DependencyConstraint>(variant.dependencyConstraints),
                    ImmutableList.copyOf(variant.files),
                    ImmutableCapabilities.Companion.of(variant.capabilities),
                    variant.isAvailableExternally
                )
            )
        }
        return builder.build()
    }

    val mutableVariants: MutableList<out MutableComponentVariant>?
        get() = newVariants

    override fun belongsTo(platform: VirtualComponentIdentifier?) {
        if (owners == null) {
            owners = LinkedHashSet<VirtualComponentIdentifier?>()
        }
        owners!!.add(platform)
    }

    val platformOwners: MutableSet<out VirtualComponentIdentifier?>?
        get() = owners

    protected class MutableVariantImpl internal constructor(val name: String?, val attributes: ImmutableAttributes?) : MutableComponentVariant {
        val dependencies: MutableList<ComponentVariant.Dependency?> = ArrayList<ComponentVariant.Dependency?>()
        val dependencyConstraints: MutableList<ComponentVariant.DependencyConstraint?> = ArrayList<ComponentVariant.DependencyConstraint?>()
        private val files: MutableList<FileImpl?> = ArrayList<FileImpl?>()
        val capabilities: MutableSet<Capability?> = LinkedHashSet<Capability?>()
        private var availableExternally = false

        override fun addDependency(
            group: String?,
            module: String?,
            versionConstraint: VersionConstraint?,
            excludes: MutableList<ExcludeMetadata?>,
            reason: String?,
            attributes: ImmutableAttributes?,
            requestedCapabilities: MutableSet<CapabilitySelector?>?,
            endorsing: Boolean,
            artifact: IvyArtifactName?
        ) {
            dependencies.add(DependencyImpl(group, module, versionConstraint, excludes, reason, attributes, requestedCapabilities, endorsing, artifact))
        }

        override fun addDependencyConstraint(group: String?, module: String?, versionConstraint: VersionConstraint?, reason: String?, attributes: ImmutableAttributes?) {
            dependencyConstraints.add(DependencyConstraintImpl(group, module, versionConstraint, reason, attributes))
        }

        override fun addCapability(group: String?, name: String?, version: String?) {
            capabilities.add(DefaultImmutableCapability(group, name, version))
        }

        override fun addCapability(capability: Capability?) {
            capabilities.add(capability)
        }

        override fun getFiles(): MutableList<out ComponentVariant.File?> {
            return files
        }

        override fun removeFile(file: ComponentVariant.File?): Boolean {
            return files.remove(file)
        }

        override fun addFile(name: String?, uri: String?) {
            files.add(FileImpl(name, uri))
        }

        override fun copy(variantName: String?, attributes: ImmutableAttributes?, capability: Capability?): MutableComponentVariant {
            val copy = MutableVariantImpl(variantName, attributes)
            copy.dependencies.addAll(this.dependencies)
            copy.dependencyConstraints.addAll(this.dependencyConstraints)
            copy.files.addAll(this.files)
            copy.capabilities.add(capability)
            copy.availableExternally = this.availableExternally
            return copy
        }

        override fun isAvailableExternally(): Boolean {
            return availableExternally
        }

        override fun setAvailableExternally(availableExternally: Boolean) {
            this.availableExternally = availableExternally
        }
    }

    class FileImpl(private val name: String?, private val uri: String?) : ComponentVariant.File {
        override fun getName(): String? {
            return name
        }

        override fun getUri(): String? {
            return uri
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val file = o as FileImpl
            return Objects.equal(name, file.name)
                    && Objects.equal(uri, file.uri)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(name, uri)
        }
    }

    protected class DependencyImpl internal constructor(
        private val group: String?,
        private val module: String?,
        private val versionConstraint: VersionConstraint?,
        excludes: MutableList<ExcludeMetadata?>,
        private val reason: String?,
        private val attributes: ImmutableAttributes?,
        private val requestedCapabilities: MutableSet<CapabilitySelector?>?,
        private val endorsing: Boolean,
        private val dependencyArtifact: IvyArtifactName?
    ) : ComponentVariant.Dependency {
        private val excludes: ImmutableList<ExcludeMetadata?>

        init {
            this.excludes = ImmutableList.copyOf<ExcludeMetadata?>(excludes)
        }

        override fun getGroup(): String? {
            return group
        }

        override fun getModule(): String? {
            return module
        }

        override fun getVersionConstraint(): VersionConstraint? {
            return versionConstraint
        }

        override fun getExcludes(): ImmutableList<ExcludeMetadata?> {
            return excludes
        }

        override fun getReason(): String? {
            return reason
        }

        override fun getAttributes(): ImmutableAttributes? {
            return attributes
        }

        override fun getCapabilitySelectors(): MutableSet<CapabilitySelector?>? {
            return requestedCapabilities
        }

        override fun isEndorsingStrictVersions(): Boolean {
            return endorsing
        }

        override fun getDependencyArtifact(): IvyArtifactName? {
            return dependencyArtifact
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as DependencyImpl
            return Objects.equal(group, that.group)
                    && Objects.equal(module, that.module)
                    && Objects.equal(versionConstraint, that.versionConstraint)
                    && Objects.equal(excludes, that.excludes)
                    && Objects.equal(reason, that.reason)
                    && Objects.equal(attributes, that.attributes)
                    && Objects.equal(requestedCapabilities, that.requestedCapabilities)
                    && endorsing == that.endorsing && Objects.equal(dependencyArtifact, that.dependencyArtifact)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(group, module, versionConstraint, excludes, reason, attributes, endorsing, dependencyArtifact)
        }
    }

    protected class DependencyConstraintImpl internal constructor(
        private val group: String?,
        private val module: String?,
        private val versionConstraint: VersionConstraint?,
        private val reason: String?,
        private val attributes: ImmutableAttributes?
    ) : ComponentVariant.DependencyConstraint {
        override fun getGroup(): String? {
            return group
        }

        override fun getModule(): String? {
            return module
        }

        override fun getVersionConstraint(): VersionConstraint? {
            return versionConstraint
        }

        override fun getReason(): String? {
            return reason
        }

        override fun getAttributes(): ImmutableAttributes? {
            return attributes
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as DependencyConstraintImpl
            return Objects.equal(group, that.group)
                    && Objects.equal(module, that.module)
                    && Objects.equal(versionConstraint, that.versionConstraint)
                    && Objects.equal(reason, that.reason)
                    && Objects.equal(attributes, that.attributes)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(group, module, versionConstraint, reason, attributes)
        }
    }

    class ImmutableVariantImpl internal constructor(
        private val componentId: ModuleComponentIdentifier,
        val name: String,
        val attributes: ImmutableAttributes?,
        private val dependencies: ImmutableList<out ComponentVariant.Dependency?>?,
        private val dependencyConstraints: ImmutableList<out ComponentVariant.DependencyConstraint?>?,
        private val files: ImmutableList<out ComponentVariant.File>,
        val capabilities: ImmutableCapabilities,
        private val externalVariant: Boolean
    ) : ComponentVariant, VariantResolveMetadata {
        val identifier: VariantResolveMetadata.Identifier
            get() = ComponentConfigurationIdentifier(componentId, name)

        override fun asDescribable(): DisplayName? {
            return Describables.of(componentId, "variant", name)
        }

        override fun getDependencies(): ImmutableList<out ComponentVariant.Dependency?>? {
            return dependencies
        }

        override fun getDependencyConstraints(): ImmutableList<out ComponentVariant.DependencyConstraint?>? {
            return dependencyConstraints
        }

        override fun getFiles(): ImmutableList<out ComponentVariant.File> {
            return files
        }

        val artifacts: ImmutableList<out ComponentArtifactMetadata?>
            get() {
                val artifacts =
                    ImmutableList.Builder<ComponentArtifactMetadata?>()
                for (file in files) {
                    artifacts.add(UrlBackedArtifactMetadata(componentId, file.getName(), file.getUri()))
                }
                return artifacts.build()
            }

        override fun isExternalVariant(): Boolean {
            return externalVariant
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ImmutableVariantImpl
            return Objects.equal(componentId, that.componentId)
                    && Objects.equal(name, that.name)
                    && Objects.equal(attributes, that.attributes)
                    && Objects.equal(dependencies, that.dependencies)
                    && Objects.equal(dependencyConstraints, that.dependencyConstraints)
                    && Objects.equal(files, that.files)
                    && externalVariant == that.externalVariant
        }

        override fun hashCode(): Int {
            return Objects.hashCode(
                componentId,
                name,
                attributes,
                dependencies,
                dependencyConstraints,
                files,
                externalVariant
            )
        }
    }

    companion object {
        private const val DEFAULT_STATUS = "integration"

        private fun defaultAttributes(attributesFactory: AttributesFactory): AttributeContainerInternal {
            return attributesFactory.mutable().attribute<String?>(ProjectInternal.STATUS_ATTRIBUTE, DEFAULT_STATUS) as AttributeContainerInternal
        }
    }
}
