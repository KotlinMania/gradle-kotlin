/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.ivy.internal.publication

import com.google.common.collect.Streams
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.VersionMappingStrategy
import org.gradle.api.publish.internal.CompositePublicationArtifactSet
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet
import org.gradle.api.publish.internal.PublicationArtifactInternal
import org.gradle.api.publish.internal.PublicationArtifactSet
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.ivy.InvalidIvyPublicationException
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyConfigurationContainer
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet
import org.gradle.api.publish.ivy.internal.artifact.DerivedIvyArtifact
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactInternal
import org.gradle.api.publish.ivy.internal.artifact.NormalizedIvyArtifact
import org.gradle.api.publish.ivy.internal.artifact.SingleOutputTaskIvyArtifact
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependencySet
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Describables
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.internal.GUtil
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Predicate
import java.util.stream.Stream
import javax.inject.Inject

abstract class DefaultIvyPublication @Inject constructor(
    private val name: String,
    instantiator: Instantiator,
    objectFactory: ObjectFactory,
    private val publicationCoordinates: IvyPublicationCoordinates,
    ivyArtifactNotationParser: NotationParser<Any, IvyArtifact>,
    fileCollectionFactory: FileCollectionFactory,
    private val attributesFactory: AttributesFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
    val versionMappingStrategy: VersionMappingStrategyInternal,
    private val taskDependencyFactory: TaskDependencyFactory,
    providerFactory: ProviderFactory
) : IvyPublicationInternal {
    val descriptor: IvyModuleDescriptorSpecInternal
    private val configurations: IvyConfigurationContainer
    private val mainArtifacts: DefaultIvyArtifactSet
    private val metadataArtifacts: PublicationArtifactSet<IvyArtifact?>
    private val derivedArtifacts: PublicationArtifactSet<IvyArtifact?>
    private val publishableArtifacts: PublicationArtifactSet<IvyArtifact?>

    private val silencedVariants: MutableSet<String> = HashSet<String>()
    private var ivyDescriptorArtifact: IvyArtifact? = null
    private var moduleDescriptorGenerator: TaskProvider<out Task>? = null
    private var gradleModuleDescriptorArtifact: SingleOutputTaskIvyArtifact? = null
    private var alias = false
    private var populated = false
    private var artifactsOverridden = false
    private var silenceAllPublicationWarnings = false
    var isPublishBuildId: Boolean = false
        private set

    init {
        val ivyComponentParser = objectFactory.newInstance<IvyComponentParser>(IvyComponentParser::class.java, ivyArtifactNotationParser)

        this.componentArtifacts.convention(this.component.map<MutableSet<IvyArtifact>>(Transformer { component: SoftwareComponentInternal? -> ivyComponentParser.parseArtifacts(component!!) }))
        this.componentArtifacts.finalizeValueOnRead()

        this.componentConfigurations.convention(this.component.map<IvyConfigurationContainer>(Transformer { component: SoftwareComponentInternal? -> ivyComponentParser.parseConfigurations(component!!) }))
        this.componentConfigurations.finalizeValueOnRead()

        this.mainArtifacts = instantiator.newInstance<DefaultIvyArtifactSet>(
            DefaultIvyArtifactSet::class.java,
            name, ivyArtifactNotationParser, fileCollectionFactory, collectionCallbackActionDecorator
        )
        this.metadataArtifacts = DefaultPublicationArtifactSet<IvyArtifact?>(IvyArtifact::class.java, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator)
        this.derivedArtifacts = DefaultPublicationArtifactSet<IvyArtifact?>(IvyArtifact::class.java, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator)
        this.publishableArtifacts = CompositePublicationArtifactSet<IvyArtifact?>(
            taskDependencyFactory,
            IvyArtifact::class.java,
            *uncheckedCast<Array<PublicationArtifactSet<IvyArtifact?>>?>(arrayOf<PublicationArtifactSet<*>>(mainArtifacts, metadataArtifacts, derivedArtifacts))!!
        )

        this.configurations = instantiator.newInstance<DefaultIvyConfigurationContainer>(DefaultIvyConfigurationContainer::class.java, instantiator, collectionCallbackActionDecorator)

        this.descriptor = objectFactory.newInstance<DefaultIvyModuleDescriptorSpec>(
            DefaultIvyModuleDescriptorSpec::class.java, objectFactory,
            publicationCoordinates
        )
        this.descriptor.setStatus(DEFAULT_STATUS)
        this.descriptor.getWriteGradleMetadataMarker().set(providerFactory.provider<Boolean>(Callable { this.writeGradleMetadataMarker() }))
        this.descriptor.getGlobalExcludes()
            .set(this.component.map<MutableSet<IvyExcludeRule>>(Transformer { component: SoftwareComponentInternal? -> ivyComponentParser.parseGlobalExcludes(component!!) }))
        this.descriptor.getConfigurations().set(this.configurations)
        this.descriptor.getArtifacts().set(providerFactory.provider<DefaultIvyArtifactSet>(Callable { this.artifacts }))
        this.descriptor.getDependencies().set(
            this.component
                .flatMap<IvyComponentParser.ParsedDependencyResult>(Transformer { component: SoftwareComponentInternal? ->
                    ivyComponentParser.parseDependencies(
                        component!!,
                        versionMappingStrategy
                    )
                })
                .map<DefaultIvyDependencySet>(Transformer { parsed: IvyComponentParser.ParsedDependencyResult? ->
                    if (!silenceAllPublicationWarnings) {
                        parsed!!.getWarnings().complete(this.displayName.toString() + " ivy metadata", silencedVariants)
                    }
                    parsed!!.getDependencies()
                })
        )
    }

    override fun getName(): String {
        return name
    }

    override fun withoutBuildIdentifier() {
        this.isPublishBuildId = false
    }

    override fun withBuildIdentifier() {
        this.isPublishBuildId = true
    }

    val displayName: DisplayName
        get() = Describables.withTypeAndName("Ivy publication", name)

    val isLegacy: Boolean
        get() = false

    abstract val component: Property<SoftwareComponentInternal>?

    override fun setIvyDescriptorGenerator(descriptorGenerator: TaskProvider<out Task>) {
        if (ivyDescriptorArtifact != null) {
            metadataArtifacts.remove(ivyDescriptorArtifact)
        }
        ivyDescriptorArtifact = SingleOutputTaskIvyArtifact(descriptorGenerator, publicationCoordinates, "xml", "ivy", null, taskDependencyFactory)
        ivyDescriptorArtifact.setName("ivy")
        metadataArtifacts.add(ivyDescriptorArtifact)
    }

    override fun setModuleDescriptorGenerator(descriptorGenerator: TaskProvider<out Task>) {
        moduleDescriptorGenerator = descriptorGenerator
        if (gradleModuleDescriptorArtifact != null) {
            metadataArtifacts.remove(gradleModuleDescriptorArtifact)
        }
        gradleModuleDescriptorArtifact = null
        updateModuleDescriptorArtifact()
    }

    private fun updateModuleDescriptorArtifact() {
        if (!canPublishModuleMetadata()) {
            return
        }
        if (moduleDescriptorGenerator == null) {
            return
        }
        gradleModuleDescriptorArtifact = SingleOutputTaskIvyArtifact(moduleDescriptorGenerator!!, publicationCoordinates, "module", "json", null, taskDependencyFactory)
        metadataArtifacts.add(gradleModuleDescriptorArtifact)
        moduleDescriptorGenerator = null
    }

    override fun descriptor(configure: Action<in IvyModuleDescriptorSpec>) {
        configure.execute(descriptor)
    }

    override fun isAlias(): Boolean {
        return alias
    }

    override fun setAlias(alias: Boolean) {
        this.alias = alias
    }

    override fun from(component: SoftwareComponent) {
        if (this.component.isPresent()) {
            throw InvalidUserDataException(String.format("Ivy publication '%s' cannot include multiple components", name))
        }
        this.component.set(component as SoftwareComponentInternal)
        this.component.finalizeValue()
        artifactsOverridden = false

        updateModuleDescriptorArtifact()
    }

    // TODO: This method should be removed in favor of lazily adding artifacts to the publication state.
    // This is currently blocked by Signing eagerly realizing the publication artifacts.
    private fun populateFromComponent() {
        if (populated) {
            return
        }
        populated = true
        if (!artifactsOverridden && this.componentArtifacts.isPresent()) {
            mainArtifacts.addAll(this.componentArtifacts.get())
        }
        if (this.componentConfigurations.isPresent()) {
            configurations.addAll(this.componentConfigurations.get())
        }
    }

    protected abstract val componentArtifacts: SetProperty<IvyArtifact>?
    protected abstract val componentConfigurations: SetProperty<IvyConfiguration>?

    override fun configurations(config: Action<in IvyConfigurationContainer>) {
        populateFromComponent()
        config.execute(configurations)
    }

    override fun getConfigurations(): IvyConfigurationContainer {
        populateFromComponent()
        return configurations
    }

    override fun artifact(source: Any): IvyArtifact {
        return mainArtifacts.artifact(source)
    }

    override fun artifact(source: Any, config: Action<in IvyArtifact>): IvyArtifact {
        return mainArtifacts.artifact(source, config)
    }

    var artifacts: DefaultIvyArtifactSet
        get() {
            populateFromComponent()
            return mainArtifacts
        }
        set(sources) {
            artifactsOverridden = true
            mainArtifacts.clear()
            for (source in sources) {
                artifact(source)
            }
        }

    var organisation: String
        get() = descriptor.getCoordinates().getOrganisation().get()
        set(organisation) {
            descriptor.getCoordinates().getOrganisation().set(organisation)
        }

    var module: String
        get() = descriptor.getCoordinates().getModule().get()
        set(module) {
            descriptor.getCoordinates().getModule().set(module)
        }

    var revision: String
        get() = descriptor.getCoordinates().getRevision().get()
        set(revision) {
            descriptor.getCoordinates().getRevision().set(revision)
        }

    override fun getPublishableArtifacts(): PublicationArtifactSet<IvyArtifact?> {
        populateFromComponent()
        return publishableArtifacts
    }

    override fun allPublishableArtifacts(action: Action<in IvyArtifact>) {
        publishableArtifacts.all(action)
    }

    override fun whenPublishableArtifactRemoved(action: Action<in IvyArtifact>) {
        publishableArtifacts.whenObjectRemoved(action)
    }

    override fun addDerivedArtifact(originalArtifact: IvyArtifact, fileProvider: PublicationInternal.DerivedArtifact): IvyArtifact {
        val effectiveFileProvider = if (originalArtifact === gradleModuleDescriptorArtifact)
            DefaultIvyPublication.GradleModuleDescriptorDerivedArtifact(fileProvider, gradleModuleDescriptorArtifact!!)
        else
            fileProvider

        val artifact: IvyArtifact = DerivedIvyArtifact(originalArtifact, effectiveFileProvider, taskDependencyFactory)
        derivedArtifacts.add(artifact)
        return artifact
    }

    override fun removeDerivedArtifact(artifact: IvyArtifact) {
        derivedArtifacts.remove(artifact)
    }

    override fun asNormalisedPublication(): IvyNormalizedPublication {
        populateFromComponent()

        // Preserve identity of artifacts
        val main: MutableSet<IvyArtifact> = Companion.linkedHashSetOf<IvyArtifact>(
            normalized(
                mainArtifacts.stream(),
                Predicate { artifact: IvyArtifact -> this.isValidArtifact(artifact) }
            )
        )
        val all = LinkedHashSet<IvyArtifact>(main)
        normalized(
            Streams.concat<IvyArtifact>(metadataArtifacts.stream(), derivedArtifacts.stream()),
            Predicate { element: IvyArtifact -> this.isPublishableArtifact(element) }
        ).forEach { e: IvyArtifact? -> all.add(e!!) }
        return IvyNormalizedPublication(
            name,
            coordinates,
            this.ivyDescriptorFile,
            all
        )
    }

    private fun isValidArtifact(artifact: IvyArtifact): Boolean {
        // Validation is done this way for backwards compatibility
        val artifactFile: File = artifact.file
        if (artifactFile == null) {
            throw InvalidIvyPublicationException(name, String.format("artifact file does not exist: '%s'", artifact))
        }
        if (!(artifact as IvyArtifactInternal).shouldBePublished()) {
            // Fail if it's the main artifact, otherwise simply disable publication
            checkNotNull(artifact.getClassifier()) { "Artifact " + artifact.file.getName() + " wasn't produced by this build." }
            return false
        }
        return true
    }

    private fun isPublishableArtifact(element: IvyArtifact): Boolean {
        if (!(element as PublicationArtifactInternal).shouldBePublished()) {
            return false
        }
        if (gradleModuleDescriptorArtifact === element) {
            // We temporarily want to allow skipping the publication of Gradle module metadata
            return gradleModuleDescriptorArtifact!!.isEnabled()
        }
        return true
    }

    override fun writeGradleMetadataMarker(): Boolean {
        return canPublishModuleMetadata()
                && gradleModuleDescriptorArtifact != null && gradleModuleDescriptorArtifact!!.isEnabled()
    }

    private fun canPublishModuleMetadata(): Boolean {
        // Cannot yet publish module metadata without component
        return this.component.isPresent()
    }

    private val ivyDescriptorFile: File
        get() {
            checkNotNull(ivyDescriptorArtifact) { "ivyDescriptorArtifact not set for publication" }
            return ivyDescriptorArtifact!!.file
        }

    val coordinates: ModuleVersionIdentifier
        get() = DefaultModuleVersionIdentifier.newId(organisation, module, revision)

    override fun <T> getCoordinates(type: Class<T?>): T? {
        if (type.isAssignableFrom(ModuleVersionIdentifier::class.java)) {
            return type.cast(coordinates)
        }
        return null
    }

    val attributes: ImmutableAttributes
        get() = attributesFactory.of<T>(ProjectInternal.STATUS_ATTRIBUTE, this.descriptor.getStatus())

    private fun getPublishedUrl(source: PublishArtifact): String {
        return getArtifactFileName(source.getClassifier(), source.getExtension())
    }

    private fun getArtifactFileName(classifier: String?, extension: String): String {
        val artifactPath = StringBuilder()
        val coordinates = coordinates
        artifactPath.append(coordinates.getName())
        artifactPath.append('-')
        artifactPath.append(coordinates.getVersion())
        if (GUtil.isTrue(classifier)) {
            artifactPath.append('-')
            artifactPath.append(classifier)
        }
        if (GUtil.isTrue(extension)) {
            artifactPath.append('.')
            artifactPath.append(extension)
        }
        return artifactPath.toString()
    }

    override fun getPublishedFile(source: PublishArtifact): PublicationInternal.PublishedFile {
        val uri = getPublishedUrl(source)
        get() = this.name
        return object : PublicationInternal.PublishedFile {
        }
    }

    override fun versionMapping(configureAction: Action<in VersionMappingStrategy>) {
        configureAction.execute(versionMappingStrategy)
    }

    override fun suppressIvyMetadataWarningsFor(variantName: String) {
        silencedVariants.add(variantName)
    }

    override fun suppressAllIvyMetadataWarnings() {
        this.silenceAllPublicationWarnings = true
    }

    private class GradleModuleDescriptorDerivedArtifact(private val derivedArtifact: PublicationInternal.DerivedArtifact, private val gradleModuleDescriptorArtifact: SingleOutputTaskIvyArtifact) :
        PublicationInternal.DerivedArtifact {
        override fun create(): File? {
            return derivedArtifact.create()
        }

        override fun shouldBePublished(): Boolean {
            return gradleModuleDescriptorArtifact.shouldBePublished() &&
                    derivedArtifact.shouldBePublished()
        }
    }

    companion object {
        const val DEFAULT_STATUS: String = "integration"

        private fun <T> linkedHashSetOf(stream: Stream<T?>): MutableSet<T?> {
            val set = LinkedHashSet<T?>()
            stream.forEach { e: T? -> set.add(e) }
            return set
        }

        private fun normalized(artifacts: Stream<IvyArtifact>, predicate: Predicate<IvyArtifact>): Stream<IvyArtifact> {
            return artifacts
                .filter(predicate)
                .map<IvyArtifact> { artifact: IvyArtifact? -> Companion.normalizedArtifactFor(artifact!!) }
        }

        private fun normalizedArtifactFor(artifact: IvyArtifact): NormalizedIvyArtifact {
            return (artifact as IvyArtifactInternal).asNormalisedArtifact()
        }
    }
}
