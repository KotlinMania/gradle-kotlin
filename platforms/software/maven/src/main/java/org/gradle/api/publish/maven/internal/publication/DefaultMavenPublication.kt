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
package org.gradle.api.publish.maven.internal.publication

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.Directory
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.CompositeDomainObjectSet
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils.inferStatusFromVersionNumber
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository.getName
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
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.internal.artifact.AbstractMavenArtifact
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet
import org.gradle.api.publish.maven.internal.artifact.DerivedMavenArtifact
import org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact
import org.gradle.api.publish.maven.internal.dependencies.MavenPomDependencies
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication
import org.gradle.api.publish.maven.internal.validation.MavenPublicationErrorChecker
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Describables
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.util.internal.GUtil
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Function
import java.util.stream.Collectors
import javax.inject.Inject

abstract class DefaultMavenPublication @Inject constructor(
    private val name: String,
    dependencyMetaDataProvider: DependencyMetaDataProvider,
    mavenArtifactParser: NotationParser<Any, MavenArtifact>,
    objectFactory: ObjectFactory,
    fileCollectionFactory: FileCollectionFactory,
    private val attributesFactory: AttributesFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
    val versionMappingStrategy: VersionMappingStrategyInternal,
    private val taskDependencyFactory: TaskDependencyFactory,
    providerFactory: ProviderFactory,
    project: Project
) : MavenPublicationInternal {
    private val projectDisplayName: String
    private val buildDir: Directory

    private val pom: MavenPomInternal
    private val mainArtifacts: DefaultMavenArtifactSet
    private val metadataArtifacts: PublicationArtifactSet<MavenArtifact?>
    private val derivedArtifacts: PublicationArtifactSet<MavenArtifact?>
    private val publishableArtifacts: PublicationArtifactSet<MavenArtifact?>

    private val silencedVariants: MutableSet<String> = HashSet<String>()
    private var pomArtifact: MavenArtifact? = null
    private var moduleMetadataArtifact: SingleOutputTaskMavenArtifact? = null
    private var moduleDescriptorGenerator: TaskProvider<out Task>? = null
    private var isPublishWithOriginalFileName = false
    private var alias = false
    private var populated = false
    private var artifactsOverridden = false
    private var silenceAllPublicationWarnings = false
    var isPublishBuildId: Boolean = false
        private set

    init {
        this.projectDisplayName = project.getDisplayName()
        this.buildDir = project.getLayout().getProjectDirectory()

        val mavenComponentParser = objectFactory.newInstance<MavenComponentParser>(MavenComponentParser::class.java, mavenArtifactParser)

        this.componentArtifacts.convention(this.component.map<MutableSet<MavenArtifact>>(Transformer { component: SoftwareComponentInternal? -> mavenComponentParser.parseArtifacts(component!!) }))
        this.componentArtifacts.finalizeValueOnRead()

        this.mainArtifacts = objectFactory.newInstance<DefaultMavenArtifactSet>(
            DefaultMavenArtifactSet::class.java,
            name, mavenArtifactParser, fileCollectionFactory, collectionCallbackActionDecorator
        )
        this.metadataArtifacts = DefaultPublicationArtifactSet<MavenArtifact?>(MavenArtifact::class.java, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator)
        this.derivedArtifacts = DefaultPublicationArtifactSet<MavenArtifact?>(MavenArtifact::class.java, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator)
        this.publishableArtifacts = CompositePublicationArtifactSet<MavenArtifact?>(
            taskDependencyFactory, MavenArtifact::class.java, *uncheckedCast<Array<PublicationArtifactSet<MavenArtifact?>>?>(
                arrayOf<PublicationArtifactSet<*>>(mainArtifacts, metadataArtifacts, derivedArtifacts)
            )!!
        )

        this.pom = objectFactory.newInstance<DefaultMavenPom>(DefaultMavenPom::class.java)
        this.pom.getWriteGradleMetadataMarker().set(providerFactory.provider<Boolean>(Callable { this.writeGradleMetadataMarker() }))
        this.pom.getPackagingProperty().convention(providerFactory.provider<String>(Callable { this.determinePackagingFromArtifacts() }))
        this.pom.getDependencies().set(
            this.component
                .flatMap<MavenComponentParser.ParsedDependencyResult>(Transformer { component: SoftwareComponentInternal? ->
                    mavenComponentParser.parseDependencies(
                        component!!,
                        versionMappingStrategy, coordinates
                    )
                })
                .map<MavenPomDependencies>(Transformer { result: MavenComponentParser.ParsedDependencyResult? ->
                    if (!silenceAllPublicationWarnings) {
                        result!!.getWarnings().complete(this.displayName.toString() + " pom metadata", silencedVariants)
                    }
                    result!!.getDependencies()
                })
        )

        val module: Module = dependencyMetaDataProvider.module
        val coordinates = pom.getCoordinates()
        coordinates.getGroupId().convention(providerFactory.provider<String>(module::getGroup))
        coordinates.getArtifactId().convention(providerFactory.provider<String>(module::getName))
        coordinates.getVersion().convention(providerFactory.provider<String>(module::getVersion))
    }

    abstract val component: Property<SoftwareComponentInternal>?

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
        get() = Describables.withTypeAndName("Maven publication", name)

    val isLegacy: Boolean
        get() = false

    override fun getPom(): MavenPomInternal {
        return pom
    }

    override fun setPomGenerator(pomGenerator: TaskProvider<out Task>) {
        if (pomArtifact != null) {
            metadataArtifacts.remove(pomArtifact)
        }
        pomArtifact = SingleOutputTaskMavenArtifact(pomGenerator, "pom", null, taskDependencyFactory)
        metadataArtifacts.add(pomArtifact)
    }

    override fun setModuleDescriptorGenerator(descriptorGenerator: TaskProvider<out Task>) {
        moduleDescriptorGenerator = descriptorGenerator
        if (moduleMetadataArtifact != null) {
            metadataArtifacts.remove(moduleMetadataArtifact)
        }
        moduleMetadataArtifact = null
        updateModuleDescriptorArtifact()
    }

    private fun updateModuleDescriptorArtifact() {
        if (!canPublishModuleMetadata()) {
            return
        }
        if (moduleDescriptorGenerator == null) {
            return
        }
        moduleMetadataArtifact = SingleOutputTaskMavenArtifact(moduleDescriptorGenerator, "module", null, taskDependencyFactory)
        metadataArtifacts.add(moduleMetadataArtifact)
        moduleDescriptorGenerator = null
    }


    override fun pom(configure: Action<in MavenPom>) {
        configure.execute(pom)
    }

    override fun isAlias(): Boolean {
        return alias
    }

    override fun setAlias(alias: Boolean) {
        this.alias = alias
    }

    override fun from(component: SoftwareComponent) {
        if (this.component.isPresent()) {
            throw InvalidUserDataException(String.format("Maven publication '%s' cannot include multiple components", name))
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
    }

    protected abstract val componentArtifacts: SetProperty<MavenArtifact>?

    override fun artifact(source: Any): MavenArtifact {
        return mainArtifacts.artifact(source)
    }

    override fun artifact(source: Any, config: Action<in MavenArtifact>): MavenArtifact {
        return mainArtifacts.artifact(source, config)
    }

    override fun getArtifacts(): MavenArtifactSet {
        populateFromComponent()
        return mainArtifacts
    }

    override fun setArtifacts(sources: Iterable<*>) {
        artifactsOverridden = true
        mainArtifacts.clear()
        for (source in sources) {
            artifact(source!!)
        }
    }

    override fun getGroupId(): String {
        return pom.getCoordinates().getGroupId().get()
    }

    override fun setGroupId(groupId: String) {
        pom.getCoordinates().getGroupId().set(groupId)
    }

    override fun getArtifactId(): String {
        return pom.getCoordinates().getArtifactId().get()
    }

    override fun setArtifactId(artifactId: String) {
        pom.getCoordinates().getArtifactId().set(artifactId)
    }

    override fun getVersion(): String {
        return pom.getCoordinates().getVersion().get()
    }

    override fun setVersion(version: String) {
        pom.getCoordinates().getVersion().set(version)
    }

    override fun versionMapping(configureAction: Action<in VersionMappingStrategy>) {
        configureAction.execute(versionMappingStrategy)
    }

    override fun suppressPomMetadataWarningsFor(variantName: String) {
        this.silencedVariants.add(variantName)
    }

    override fun suppressAllPomMetadataWarnings() {
        this.silenceAllPublicationWarnings = true
    }

    private fun writeGradleMetadataMarker(): Boolean {
        return canPublishModuleMetadata() && moduleMetadataArtifact != null && moduleMetadataArtifact!!.isEnabled()
    }

    override fun getPublishableArtifacts(): PublicationArtifactSet<MavenArtifact?> {
        populateFromComponent()
        return publishableArtifacts
    }

    override fun allPublishableArtifacts(action: Action<in MavenArtifact>) {
        publishableArtifacts.all(action)
    }

    override fun whenPublishableArtifactRemoved(action: Action<in MavenArtifact>) {
        publishableArtifacts.whenObjectRemoved(action)
    }

    override fun addDerivedArtifact(originalArtifact: MavenArtifact, file: PublicationInternal.DerivedArtifact): MavenArtifact {
        val artifact: MavenArtifact = DerivedMavenArtifact(originalArtifact as AbstractMavenArtifact, file, taskDependencyFactory)
        derivedArtifacts.add(artifact)
        return artifact
    }

    override fun removeDerivedArtifact(artifact: MavenArtifact) {
        derivedArtifacts.remove(artifact)
    }

    override fun asNormalisedPublication(): MavenNormalizedPublication {
        populateFromComponent()

        // Preserve identity of artifacts
        val normalizedArtifacts = normalizedMavenArtifacts()

        return MavenNormalizedPublication(
            name,
            pom.getCoordinates(),
            pom.getPackaging(),
            normalizedArtifactFor(getPomArtifact(), normalizedArtifacts),
            normalizedArtifactFor(determineMainArtifact(), normalizedArtifacts),
            LinkedHashSet<MavenArtifact?>(normalizedArtifacts.values)
        )
    }

    private fun normalizedMavenArtifacts(): MutableMap<MavenArtifact, MavenArtifact> {
        return artifactsToBePublished()
            .stream()
            .collect(
                Collectors.toMap(
                    Function.identity<MavenArtifact>(),
                    Function { artifact: MavenArtifact? -> Companion.normalizedArtifactFor(artifact!!) }
                ))
    }

    private fun artifactsToBePublished(): DomainObjectSet<MavenArtifact> {
        return CompositeDomainObjectSet.create<MavenArtifact>(
            MavenArtifact::class.java,
            *uncheckedCast<Array<DomainObjectCollection<out MavenArtifact>>?>(
                arrayOf<DomainObjectCollection<*>>(mainArtifacts, metadataArtifacts, derivedArtifacts)
            )
        ).matching(org.gradle.api.specs.Spec { element: MavenArtifact? ->
            if (!(element as PublicationArtifactInternal).shouldBePublished()) {
                return@matching false
            }
            if (moduleMetadataArtifact === element) {
                // We temporarily want to allow skipping the publication of Gradle module metadata
                return@matching moduleMetadataArtifact!!.isEnabled()
            }
            true
        })
    }

    private fun getPomArtifact(): MavenArtifact {
        checkNotNull(pomArtifact) { "pomArtifact not set for publication" }
        return pomArtifact!!
    }

    // TODO Remove this attempt to guess packaging from artifacts. Packaging should come from component, or be explicitly set.
    private fun determinePackagingFromArtifacts(): String {
        val unclassifiedArtifacts = this.unclassifiedArtifactsWithExtension
        if (unclassifiedArtifacts.size == 1) {
            return unclassifiedArtifacts.iterator().next().getExtension()
        }
        return "pom"
    }

    private fun determineMainArtifact(): MavenArtifact? {
        val unclassifiedArtifacts = this.unclassifiedArtifactsWithExtension
        if (unclassifiedArtifacts.isEmpty()) {
            return null
        }
        if (unclassifiedArtifacts.size == 1) {
            // Pom packaging doesn't matter when we have a single unclassified artifact
            return unclassifiedArtifacts.iterator().next()
        }
        for (unclassifiedArtifact in unclassifiedArtifacts) {
            // With multiple unclassified artifacts, choose the one with extension matching pom packaging
            val packaging = pom.getPackaging()
            if (unclassifiedArtifact.getExtension() == packaging) {
                return unclassifiedArtifact
            }
        }
        return null
    }

    private val unclassifiedArtifactsWithExtension: MutableSet<MavenArtifact>
        get() {
            populateFromComponent()
            return filter<MavenArtifact?>(
                mainArtifacts,
                org.gradle.api.specs.Spec { mavenArtifact: MavenArtifact? ->
                    Companion.hasNoClassifier(mavenArtifact!!) && Companion.hasExtension(
                        mavenArtifact
                    )
                })
        }

    val coordinates: ModuleVersionIdentifier
        get() = DefaultModuleVersionIdentifier.newId(getGroupId(), getArtifactId(), getVersion())

    override fun <T> getCoordinates(type: Class<T?>): T? {
        if (type.isAssignableFrom(ModuleVersionIdentifier::class.java)) {
            return type.cast(coordinates)
        }
        return null
    }

    override fun publishWithOriginalFileName() {
        this.isPublishWithOriginalFileName = true
    }

    private fun canPublishModuleMetadata(): Boolean {
        // Cannot yet publish module metadata without component
        return this.component.isPresent()
    }

    override fun getPublishedFile(source: PublishArtifact): PublicationInternal.PublishedFile {
        populateFromComponent()
        if (this.component.isPresent()) {
            MavenPublicationErrorChecker.checkThatArtifactIsPublishedUnmodified(
                projectDisplayName, buildDir.getAsFile().toPath().toAbsolutePath(), this.component.get().getName(),
                source, mainArtifacts
            )
        }
        val uri = getPublishedUrl(source)
        val name = if (isPublishWithOriginalFileName) source.getFile().getName() else this.uri
        return object : PublicationInternal.PublishedFile {
        }
    }

    val attributes: ImmutableAttributes?
        get() {
            val version = pom.getCoordinates().getVersion().get()
            val status = inferStatusFromVersionNumber(version)
            return attributesFactory.of<String>(ProjectInternal.STATUS_ATTRIBUTE, status)
        }

    private fun getPublishedUrl(source: PublishArtifact): String {
        return getArtifactFileName(source.getClassifier()!!, source.getExtension())
    }

    private fun getArtifactFileName(classifier: String, extension: String): String {
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

    private class SerializableMavenArtifact(artifact: MavenArtifact) : MavenArtifact, PublicationArtifactInternal {
        val file: File
        private val extension: String
        private val classifier: String
        private val shouldBePublished: Boolean

        init {
            val artifactInternal = artifact as PublicationArtifactInternal
            this.file = artifact.file
            this.extension = artifact.getExtension()
            this.classifier = artifact.getClassifier()!!
            this.shouldBePublished = artifactInternal.shouldBePublished()
        }

        override fun getExtension(): String {
            return extension
        }

        override fun setExtension(extension: String) {
            throw IllegalStateException()
        }

        override fun getClassifier(): String? {
            return classifier
        }

        override fun setClassifier(classifier: String?) {
            throw IllegalStateException()
        }

        override fun builtBy(vararg tasks: Any) {
            throw IllegalStateException()
        }

        override fun getBuildDependencies(): TaskDependency? {
            throw IllegalStateException()
        }

        override fun shouldBePublished(): Boolean {
            return shouldBePublished
        }
    }

    companion object {
        private fun normalizedArtifactFor(artifact: MavenArtifact?, normalizedArtifacts: MutableMap<MavenArtifact, MavenArtifact>): MavenArtifact? {
            if (artifact == null) {
                return null
            }
            val normalized = normalizedArtifacts.get(artifact)
            if (normalized != null) {
                return normalized
            }
            return normalizedArtifactFor(artifact)
        }

        private fun normalizedArtifactFor(artifact: MavenArtifact): MavenArtifact {
            // TODO: introduce something like a NormalizedMavenArtifact to capture the required MavenArtifact
            //  information and only that instead of having MavenArtifact references in
            //  MavenNormalizedPublication
            return SerializableMavenArtifact(artifact)
        }

        private fun hasNoClassifier(element: MavenArtifact): Boolean {
            return element.getClassifier() == null || element.getClassifier()!!.length == 0
        }

        private fun hasExtension(element: MavenArtifact): Boolean {
            return element.getExtension() != null && element.getExtension().length > 0
        }
    }
}
