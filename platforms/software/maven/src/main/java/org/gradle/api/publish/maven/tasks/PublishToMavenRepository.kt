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
package org.gradle.api.publish.maven.tasks

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.Credentials
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.internal.PublishOperation
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.authentication.Authentication
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.serialization.Cached
import org.gradle.internal.serialization.Transient.Companion.varOf
import org.gradle.internal.service.ServiceRegistry
import org.gradle.work.DisableCachingByDefault
import java.io.Serializable
import java.net.URI
import javax.inject.Inject

/**
 * Publishes a [MavenPublication] to a [MavenArtifactRepository].
 *
 * @since 1.4
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class PublishToMavenRepository : AbstractPublishToMaven() {
    private val repository = varOf<DefaultMavenArtifactRepository?>()
    private val spec = Cached.of({ this.computeSpec() })

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun getRepository(): MavenArtifactRepository? {
        return repository.get()
    }

    @get:Optional
    @get:Nested
    abstract val credentials: Property<Credentials?>?

    @get:Inject
    protected abstract val listenerManager: ListenerManager?

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     */
    fun setRepository(repository: MavenArtifactRepository) {
        this.repository.set(repository as DefaultMavenArtifactRepository?)
        this.credentials.set(repository.getConfiguredCredentials())
    }

    @TaskAction
    fun publish() {
        val spec = this.spec.get()
        val publication = spec.publication
        val repository = spec.repository.get(getServices())
        getDuplicatePublicationTracker().checkCanPublish(publication, repository.getUrl(), repository.getName())
        doPublish(publication, repository)
    }

    private fun computeSpec(): PublishSpec {
        val publicationInternal = getPublicationInternal()
        if (publicationInternal == null) {
            throw InvalidUserDataException("The 'publication' property is required")
        }

        val repository: DefaultMavenArtifactRepository? = this.repository.get()
        if (repository == null) {
            throw InvalidUserDataException("The 'repository' property is required")
        }
        val normalizedPublication = publicationInternal.asNormalisedPublication()
        return PublishSpec(
            RepositorySpec.Companion.of(repository),
            normalizedPublication
        )
    }

    private fun doPublish(normalizedPublication: MavenNormalizedPublication, repository: MavenArtifactRepository) {
        object : PublishOperation(normalizedPublication.getName(), repository.getName()) {
            override fun publish() {
                validatingMavenPublisher().publish(normalizedPublication, repository)
            }
        }.run()
    }

    private fun validatingMavenPublisher(): MavenPublisher {
        return ValidatingMavenPublisher(
            getMavenPublishers().getRemotePublisher(getTemporaryDirFactory())
        )
    }

    internal class PublishSpec(
        private val repository: RepositorySpec,
        private val publication: MavenNormalizedPublication
    )

    internal abstract class RepositorySpec {
        abstract fun get(services: ServiceRegistry?): MavenArtifactRepository

        internal class Configured(val repository: DefaultMavenArtifactRepository) : RepositorySpec(), Serializable {
            override fun get(services: ServiceRegistry?): MavenArtifactRepository {
                return repository
            }

            private fun writeReplace(): Any {
                val credentialsSpec =
                    repository.getConfiguredCredentials().map<CredentialsSpec?>(Transformer { it: Credentials? -> CredentialsSpec.Companion.of(repository.getName(), it) }).getOrNull()
                return RepositorySpec.DefaultRepositorySpec(repository.getName(), repository.getUrl(), repository.isAllowInsecureProtocol(), credentialsSpec, repository.getConfiguredAuthentication())
            }
        }

        internal class DefaultRepositorySpec(
            private val name: String,
            private val repositoryUrl: URI,
            private val allowInsecureProtocol: Boolean,
            private val credentials: CredentialsSpec?,
            private val authentications: MutableCollection<Authentication?>
        ) : RepositorySpec() {
            override fun get(services: ServiceRegistry): MavenArtifactRepository {
                val repository = services.get<BaseRepositoryFactory?>(BaseRepositoryFactory::class.java)!!.createMavenRepository() as DefaultMavenArtifactRepository?
                repository!!.setName(name)
                repository.setUrl(repositoryUrl)
                repository.setAllowInsecureProtocol(allowInsecureProtocol)
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
            fun of(repository: DefaultMavenArtifactRepository): RepositorySpec {
                return Configured(repository)
            }
        }
    }
}
