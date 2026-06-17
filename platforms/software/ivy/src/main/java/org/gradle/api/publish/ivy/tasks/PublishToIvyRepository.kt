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
package org.gradle.api.publish.ivy.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.credentials.Credentials
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository
import org.gradle.api.internal.artifacts.repositories.layout.AbstractRepositoryLayout
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.internal.PublishOperation
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication
import org.gradle.api.publish.ivy.internal.publisher.IvyPublisher
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.authentication.Authentication
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.serialization.Cached
import org.gradle.internal.serialization.Transient.Companion.varOf
import org.gradle.internal.service.ServiceRegistry
import org.gradle.work.DisableCachingByDefault
import java.io.Serializable
import java.net.URI
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Publishes an IvyPublication to an IvyArtifactRepository.
 *
 * @since 1.3
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class PublishToIvyRepository : DefaultTask() {
    private val publication = varOf<IvyPublicationInternal?>()
    private val repository = varOf<DefaultIvyArtifactRepository?>()
    private val spec = Cached.of({ this.computeSpec() })

    init {
        // Allow the publication to participate in incremental build

        getInputs().files(Callable {
            val publicationInternal = this.publicationInternal
            if (publicationInternal == null) null else publicationInternal.publishableArtifacts!!.files
        } as Callable<FileCollection?>)
            .withPropertyName("publication.publishableFiles")
            .withPathSensitivity(PathSensitivity.NAME_ONLY)

        // Should repositories be able to participate in incremental?
        // At the least, they may be able to express themselves as output files
        // They *might* have input files and other dependencies as well though
        // Inputs: The credentials they need may be expressed in a file
        // Dependencies: Can't think of a case here
    }

    /**
     * The publication to be published.
     *
     * @return The publication to be published
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun getPublication(): IvyPublication? {
        return publication.get()
    }

    /**
     * Sets the publication to be published.
     *
     * @param publication The publication to be published
     */
    fun setPublication(publication: IvyPublication?) {
        this.publication.set(toPublicationInternal(publication))
    }

    private val publicationInternal: IvyPublicationInternal?
        get() = toPublicationInternal(getPublication())

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun getRepository(): IvyArtifactRepository? {
        return repository.get()
    }

    @get:Optional
    @get:Nested
    abstract val credentials: Property<Credentials?>?

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     */
    fun setRepository(repository: IvyArtifactRepository) {
        this.repository.set(repository as DefaultIvyArtifactRepository?)
        this.credentials.set(repository.getConfiguredCredentials())
    }

    @TaskAction
    fun publish() {
        val spec = this.spec.get()
        val publication = spec.publication
        val repository = spec.repository.get(getServices())
        this.duplicatePublicationTracker.checkCanPublish(publication, repository.getUrl(), repository.getName())
        doPublish(publication, repository)
    }

    private fun computeSpec(): PublishSpec {
        val publicationInternal = this.publicationInternal
        if (publicationInternal == null) {
            throw InvalidUserDataException("The 'publication' property is required")
        }

        val repository: DefaultIvyArtifactRepository? = this.repository.get()
        if (repository == null) {
            throw InvalidUserDataException("The 'repository' property is required")
        }
        val normalizedPublication = publicationInternal.asNormalisedPublication()
        return PublishSpec(
            RepositorySpec.Companion.of(repository),
            normalizedPublication
        )
    }

    @get:Inject
    protected abstract val ivyPublisher: IvyPublisher

    private fun doPublish(normalizedPublication: IvyNormalizedPublication, repository: IvyArtifactRepository) {
        object : PublishOperation(normalizedPublication.name, repository.getName()) {
            override fun publish() {
                val publisher: IvyPublisher = this.ivyPublisher
                publisher.publish(normalizedPublication, repository)
            }
        }.run()
    }

    internal class PublishSpec(
        private val repository: RepositorySpec,
        private val publication: IvyNormalizedPublication
    )

    internal abstract class RepositorySpec {
        abstract fun get(services: ServiceRegistry?): IvyArtifactRepository

        internal class Configured(val repository: DefaultIvyArtifactRepository) : RepositorySpec(), Serializable {
            override fun get(services: ServiceRegistry?): IvyArtifactRepository {
                return repository
            }

            private fun writeReplace(): Any {
                return RepositorySpec.DefaultRepositorySpec(
                    repository.getName(),
                    repository.getUrl(),
                    repository.isAllowInsecureProtocol(),
                    credentialsSpec(),
                    repository.repositoryLayout,
                    repository.additionalArtifactPatterns(),
                    repository.additionalIvyPatterns(),
                    repository.getConfiguredAuthentication()
                )
            }

            private fun credentialsSpec(): CredentialsSpec? {
                return repository.getConfiguredCredentials().map<CredentialsSpec?>(
                    Transformer { credentials: Credentials? -> CredentialsSpec.Companion.of(repository.getName(), credentials) }
                ).getOrNull()
            }
        }

        internal class DefaultRepositorySpec(
            private val name: String,
            private val repositoryUrl: URI,
            private val allowInsecureProtocol: Boolean,
            private val credentials: CredentialsSpec?,
            private val layout: AbstractRepositoryLayout,
            private val artifactPatterns: MutableSet<String?>,
            private val ivyPatterns: MutableSet<String?>,
            private val authentications: MutableCollection<Authentication?>
        ) : RepositorySpec() {
            override fun get(services: ServiceRegistry): IvyArtifactRepository {
                val repository = services.get<BaseRepositoryFactory?>(BaseRepositoryFactory::class.java)!!.createIvyRepository() as DefaultIvyArtifactRepository?
                repository!!.setName(name)
                repository.setUrl(repositoryUrl)
                artifactPatterns.forEach(Consumer { pattern: String? -> repository.artifactPattern(pattern!!) })
                ivyPatterns.forEach(Consumer { pattern: String? -> repository.ivyPattern(pattern!!) })
                repository.setAllowInsecureProtocol(allowInsecureProtocol)
                repository.repositoryLayout = layout
                if (credentials != null) {
                    val provider: Provider<out Credentials?> = services.get<ProviderFactory?>(ProviderFactory::class.java).this!!.credentials(
                        credentials.type, name
                    )
                    repository.setConfiguredCredentials(provider.get())
                }
                repository.authentication(Action { container: AuthenticationContainer? -> container!!.addAll(authentications) })
                return repository
            }
        }

        internal class CredentialsSpec private constructor(val identity: String?, val type: Class<out Credentials?>?) {
            companion object {
                fun of(identity: String?, credentials: Credentials?): CredentialsSpec {
                    return CredentialsSpec(identity, GeneratedSubclasses.unpackType(credentials) as Class<out Credentials?>?)
                }
            }
        }

        companion object {
            fun of(repository: DefaultIvyArtifactRepository): RepositorySpec {
                return Configured(repository)
            }
        }
    }

    @get:Inject
    protected abstract val duplicatePublicationTracker: IvyDuplicatePublicationTracker?

    companion object {
        private fun toPublicationInternal(publication: IvyPublication?): IvyPublicationInternal? {
            if (publication == null) {
                return null
            } else if (publication is IvyPublicationInternal) {
                return publication
            } else {
                throw InvalidUserDataException(
                    String.format(
                        "publication objects must implement the '%s' interface, implementation '%s' does not",
                        IvyPublicationInternal::class.java.getName(),
                        publication.javaClass.getName()
                    )
                )
            }
        }
    }
}
