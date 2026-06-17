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
package org.gradle.api.internal.artifacts.repositories.resolver

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ComponentMetadataListerDetails
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.repositories.descriptor.UrlRepositoryDescriptor
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DefaultModuleDescriptorArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resolve.result.BuildableTypedResolveResult
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.ByteArrayReadableContent
import org.gradle.internal.resource.local.FileReadableContent
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.CollectionUtils.collect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Boolean
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import java.util.function.Function
import kotlin.Any
import kotlin.ByteArray
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.String
import kotlin.UnsupportedOperationException

abstract class ExternalResourceResolver protected constructor(
    descriptor: UrlRepositoryDescriptor,
    private val local: Boolean,
    protected val repository: ExternalResourceRepository,
    private val cachingResourceAccessor: CacheAwareExternalResourceAccessor,
    private val locallyAvailableResourceFinder: LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata>,
    private val artifactFileStore: FileStore<ModuleComponentArtifactIdentifier>,
    private val metadataSources: ImmutableMetadataSources,
    private val metadataArtifactProvider: MetadataArtifactProvider,
    private val componentMetadataSupplierFactory: InstantiatingAction<ComponentMetadataSupplierDetails>?,
    private val providedVersionLister: InstantiatingAction<ComponentMetadataListerDetails>?,
    private val injector: Instantiator,
    private val checksumService: ChecksumService,
    private val continueOnConnectionFailure: Boolean
) : ConfiguredModuleComponentRepository {
    private val name: String
    private val ivyPatterns: ImmutableList<ResourcePattern>
    private val artifactPatterns: ImmutableList<ResourcePattern>
    private var componentResolvers: ComponentResolvers? = null

    private val id: String
    private var cachedArtifactResolver: ExternalResourceArtifactResolver? = null

    init {
        this.id = descriptor.getId()
        this.name = descriptor.getName()
        this.ivyPatterns = descriptor.getMetadataResources()
        this.artifactPatterns = descriptor.getArtifactResources()
    }

    override fun getId(): String {
        return id
    }

    override fun getName(): String {
        return name
    }

    override fun isDynamicResolveMode(): Boolean {
        return false
    }

    override fun isContinueOnConnectionFailure(): Boolean {
        return continueOnConnectionFailure
    }

    override fun isRepositoryDisabled(): Boolean {
        // A repository is never disabled by default
        return false
    }

    override fun setComponentResolvers(resolver: ComponentResolvers) {
        this.componentResolvers = resolver
    }

    override fun isLocal(): Boolean {
        return local
    }

    override fun getComponentMetadataInstantiator(): Instantiator {
        return injector
    }

    override fun getComponentMetadataSupplier(): InstantiatingAction<ComponentMetadataSupplierDetails> {
        return componentMetadataSupplierFactory!!
    }

    @VisibleForTesting
    fun getProvidedVersionLister(): InstantiatingAction<ComponentMetadataListerDetails> {
        return providedVersionLister!!
    }

    override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier, ResolvableArtifact>? {
        throw UnsupportedOperationException()
    }

    private fun doListModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata, result: BuildableModuleVersionListingResolveResult) {
        val module = selector.getModuleIdentifier()

        tryListingViaRule(module, result)

        if (result.hasResult() && result.isAuthoritative) {
            return
        }

        // TODO: Provide an abstraction for accessing resources within the same module (maven-metadata, directory listing, etc)
        // That way we can avoid passing `ivyPatterns` and `artifactPatterns` around everywhere
        val versionLister = ResourceVersionLister(repository)
        val completeIvyPatterns = filterComplete(this.ivyPatterns, module)
        val completeArtifactPatterns = filterComplete(this.artifactPatterns, module)

        // Iterate over the metadata sources to see if they can provide the version list
        for (metadataSource in metadataSources.sources()) {
            metadataSource.listModuleVersions(selector, overrideMetadata, completeIvyPatterns, completeArtifactPatterns, versionLister, result)
            if (result.hasResult() && result.isAuthoritative) {
                return
            }
        }

        result.listed(ImmutableSet.of<String?>())
    }

    /**
     * If the repository provides a rule to create a list of versions of a module, use it.
     * It's assumed that the result of such a call is authoritative.
     */
    private fun tryListingViaRule(module: ModuleIdentifier, result: BuildableModuleVersionListingResolveResult) {
        if (providedVersionLister != null) {
            providedVersionLister.execute(DefaultComponentVersionsLister(module, result))
        }
    }

    private fun filterComplete(ivyPatterns: MutableList<ResourcePattern>, module: ModuleIdentifier): MutableList<ResourcePattern> {
        return CollectionUtils.filter<ResourcePattern?>(ivyPatterns, org.gradle.api.specs.Spec { element: ResourcePattern? -> element!!.isComplete(module) })
    }

    protected open fun doResolveComponentMetaData(
        moduleComponentIdentifier: ModuleComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
    ) {
        resolveStaticDependency(moduleComponentIdentifier, prescribedMetaData, result, createArtifactResolver())
    }

    protected fun resolveStaticDependency(
        moduleVersionIdentifier: ModuleComponentIdentifier,
        prescribedMetaData: ComponentOverrideMetadata,
        result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>,
        artifactResolver: ExternalResourceArtifactResolver
    ) {
        for (source in metadataSources.sources()) {
            val value: MutableModuleComponentResolveMetadata? = source.create(name, componentResolvers!!, moduleVersionIdentifier, prescribedMetaData, artifactResolver, result)
            if (value != null) {
                maybeDisableComponentMetadataRuleCaching(value)
                result.resolved(value.asImmutable())
                return
            }
        }

        LOGGER.debug("No meta-data file or artifact found for module '{}' in repository '{}'.", moduleVersionIdentifier, getName())
        result.missing()
    }

    private fun maybeDisableComponentMetadataRuleCaching(value: MutableModuleComponentResolveMetadata) {
        if (isLocal()) {
            // Caching component metadata rules for local repositories leads to issues
            // when in some cases cached file does not exist yet, but we anyway try to use it
            value.isComponentMetadataRuleCachingEnabled = false
        }
    }

    protected abstract fun isMetaDataArtifact(artifactType: ArtifactType): Boolean

    protected fun findOptionalArtifacts(module: ComponentArtifactResolveMetadata, type: String, classifier: String): MutableSet<ModuleComponentArtifactMetadata> {
        if (module.getId() !is ModuleComponentIdentifier) {
            return mutableSetOf<ModuleComponentArtifactMetadata>()
        }

        val moduleId = module.getId() as ModuleComponentIdentifier?
        val ivyArtifactName: IvyArtifactName = DefaultIvyArtifactName(moduleId!!.getModule(), type, "jar", classifier)

        val artifact: ModuleComponentArtifactMetadata = DefaultModuleComponentArtifactMetadata(moduleId, ivyArtifactName)
        if (createArtifactResolver(module.getSources()!!).artifactExists(artifact, DefaultResourceAwareResolveResult())) {
            return ImmutableSet.of<ModuleComponentArtifactMetadata>(artifact)
        }
        return mutableSetOf<ModuleComponentArtifactMetadata>()
    }

    private fun getMetaDataArtifactFor(moduleComponentIdentifier: ModuleComponentIdentifier): ModuleDescriptorArtifactMetadata {
        val ivyArtifactName = metadataArtifactProvider.getMetaDataArtifactName(moduleComponentIdentifier.getModule())
        return DefaultModuleDescriptorArtifactMetadata(moduleComponentIdentifier, ivyArtifactName)
    }

    protected open fun createArtifactResolver(): ExternalResourceArtifactResolver {
        if (cachedArtifactResolver != null) {
            return cachedArtifactResolver!!
        }
        val artifactResolver = createArtifactResolver(ivyPatterns, artifactPatterns)
        cachedArtifactResolver = artifactResolver
        return artifactResolver
    }

    private fun createArtifactResolver(ivyPatterns: MutableList<ResourcePattern>, artifactPatterns: MutableList<ResourcePattern>): ExternalResourceArtifactResolver {
        return DefaultExternalResourceArtifactResolver(repository, locallyAvailableResourceFinder, ivyPatterns, artifactPatterns, artifactFileStore, cachingResourceAccessor)
    }

    protected open fun createArtifactResolver(moduleSources: ModuleSources): ExternalResourceArtifactResolver {
        return createArtifactResolver()
    }

    fun publish(artifact: ModuleComponentArtifactMetadata, src: File) {
        val destinationPattern: ResourcePattern?
        if ("ivy" == artifact.getName()!!.type && !ivyPatterns.isEmpty()) {
            destinationPattern = ivyPatterns.get(0)
        } else if (!artifactPatterns.isEmpty()) {
            destinationPattern = artifactPatterns.get(0)
        } else {
            throw IllegalStateException("impossible to publish " + artifact + " using " + this + ": no artifact pattern defined")
        }
        val destination = destinationPattern.getLocation(artifact)

        put(src, destination)
        LOGGER.info("Published {} to {}", artifact, destination)
    }

    private fun put(src: File, destination: ExternalResourceName) {
        repository.withProgressLogging().resource(destination).put(FileReadableContent(src))
        publishChecksums(destination, src)
    }

    private fun publishChecksums(destination: ExternalResourceName, content: File) {
        publishChecksum(destination, content, "sha1")

        if (!disableExtraChecksums()) {
            publishPossiblyUnsupportedChecksum(destination, content, "sha-256")
            publishPossiblyUnsupportedChecksum(destination, content, "sha-512")
        }
    }

    private fun publishPossiblyUnsupportedChecksum(destination: ExternalResourceName, content: File, algorithm: String) {
        try {
            publishChecksum(destination, content, algorithm)
        } catch (ex: Exception) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.warn("Cannot upload checksum for " + content.getName() + " because the remote repository doesn't support " + algorithm + ". This will not fail the build.", ex)
            } else {
                LOGGER.warn("Cannot upload checksum for " + content.getName() + " because the remote repository doesn't support " + algorithm + ". This will not fail the build.")
            }
        }
    }

    private fun publishChecksum(destination: ExternalResourceName, content: File, algorithm: String) {
        val checksum = createChecksumFile(content, algorithm.uppercase())
        val checksumDestination = destination.append("." + algorithm.replace("-".toRegex(), ""))
        repository.resource(checksumDestination).put(ByteArrayReadableContent(checksum))
    }

    private fun createChecksumFile(src: File, algorithm: String): ByteArray {
        val hash = checksumService.hash(src, algorithm)
        val formattedHashString = hash.toString()
        return formattedHashString.toByteArray(StandardCharsets.US_ASCII)
    }

    fun getIvyPatterns(): MutableList<String> {
        return collect<String?, ResourcePattern?>(ivyPatterns, Function { obj: ResourcePattern? -> obj!!.getPattern() })
    }

    fun getArtifactPatterns(): MutableList<String> {
        return collect<String?, ResourcePattern?>(artifactPatterns, Function { obj: ResourcePattern? -> obj!!.getPattern() })
    }

    protected abstract inner class AbstractRepositoryAccess : ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            if (artifactType == ArtifactType.JAVADOC) {
                resolveJavadocArtifacts(component, result)
            } else if (artifactType == ArtifactType.SOURCES) {
                resolveSourceArtifacts(component, result)
            } else if (isMetaDataArtifact(artifactType)) {
                resolveMetaDataArtifacts(component, result)
            }
        }

        protected abstract fun resolveMetaDataArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult)

        protected abstract fun resolveJavadocArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult)

        protected abstract fun resolveSourceArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult)
    }

    protected abstract inner class LocalRepositoryAccess : AbstractRepositoryAccess() {
        override fun toString(): String {
            return "local > " + this@ExternalResourceResolver
        }

        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata, result: BuildableModuleVersionListingResolveResult) {
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
        ) {
        }

        override fun resolveMetaDataArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            if (module.getId() !is ModuleComponentIdentifier) {
                return
            }

            val moduleId = module.getId() as ModuleComponentIdentifier?
            val artifact = getMetaDataArtifactFor(moduleId!!)
            result.resolved(mutableSetOf<ModuleDescriptorArtifactMetadata>(artifact))
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier): MetadataFetchingCost {
            return MetadataFetchingCost.CHEAP
        }
    }

    protected abstract inner class RemoteRepositoryAccess : AbstractRepositoryAccess() {
        override fun toString(): String {
            return "remote > " + this@ExternalResourceResolver
        }

        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata, result: BuildableModuleVersionListingResolveResult) {
            doListModuleVersions(selector, overrideMetadata, result)
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
        ) {
            doResolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            super.resolveArtifactsWithType(component, artifactType, result)
            checkArtifactsResolved(component, artifactType, result)
        }

        private fun checkArtifactsResolved(component: ComponentArtifactResolveMetadata, context: Any, result: BuildableTypedResolveResult<*, in ArtifactResolveException?>) {
            if (!result.hasResult()) {
                result.failed(
                    ArtifactResolveException(
                        component.getId()!!,
                        String.format("Cannot locate %s for '%s' in repository '%s'", context, component.getId()!!.getDisplayName(), name)
                    )
                )
            }
        }

        override fun resolveMetaDataArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            // Meta data artifacts are determined locally
        }

        override fun resolveJavadocArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"))
        }

        override fun resolveSourceArtifacts(module: ComponentArtifactResolveMetadata, result: BuildableArtifactSetResolveResult) {
            // Probe for artifact with classifier
            result.resolved(findOptionalArtifacts(module, "source", "sources"))
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
            if (artifact.isOptionalArtifact() && artifact is ModuleComponentArtifactMetadata) {
                if (!createArtifactResolver(moduleSources).artifactExists(artifact, DefaultResourceAwareResolveResult())) {
                    result.notFound(artifact.getId())
                    return
                }
            } else if (artifact.getAlternativeArtifact().isPresent()) {
                val checkForArtifact = DefaultResourceAwareResolveResult()
                if (!createArtifactResolver(moduleSources).artifactExists(artifact as ModuleComponentArtifactMetadata, checkForArtifact)) {
                    checkForArtifact.getAttempted().forEach(Consumer { locationDescription: String? -> result.attempted(locationDescription) })
                    resolveArtifact(artifact.getAlternativeArtifact().get(), moduleSources, result)
                    return
                }
            }
            try {
                val resolver = createArtifactResolver(moduleSources)
                val moduleArtifact = artifact as ModuleComponentArtifactMetadata
                val artifactResource = resolver.resolveArtifact(moduleArtifact, result)
                if (artifactResource == null) {
                    result.notFound(artifact.getId())
                } else {
                    result.resolved(artifactResource.getFile())
                }
            } catch (e: Exception) {
                result.failed(ArtifactResolveException(artifact.getId()!!, e))
            }
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier): MetadataFetchingCost {
            if (this@ExternalResourceResolver.local) {
                val artifact: ModuleComponentArtifactMetadata = getMetaDataArtifactFor(moduleComponentIdentifier)
                if (createArtifactResolver().artifactExists(artifact, NoOpResourceAwareResolveResult.Companion.INSTANCE)) {
                    return MetadataFetchingCost.FAST
                }
                return MetadataFetchingCost.CHEAP
            }
            return MetadataFetchingCost.EXPENSIVE
        }
    }

    private class NoOpResourceAwareResolveResult : ResourceAwareResolveResult {
        val attempted: MutableList<String>
            get() = mutableListOf<String>()

        override fun attempted(locationDescription: String) {
        }

        override fun attempted(location: ExternalResourceName) {
        }

        override fun applyTo(target: ResourceAwareResolveResult) {
            throw UnsupportedOperationException()
        }

        companion object {
            private val INSTANCE = NoOpResourceAwareResolveResult()
        }
    }

    private class DefaultComponentVersionsLister(private val id: ModuleIdentifier, private val result: BuildableModuleVersionListingResolveResult) : ComponentMetadataListerDetails {
        override fun getModuleIdentifier(): ModuleIdentifier {
            return id
        }

        override fun listed(versions: MutableList<String>) {
            result.listed(versions)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ExternalResourceResolver::class.java)

        @JvmStatic
        fun disableExtraChecksums(): Boolean {
            return Boolean.getBoolean("org.gradle.internal.publish.checksums.insecure")
        }
    }
}
