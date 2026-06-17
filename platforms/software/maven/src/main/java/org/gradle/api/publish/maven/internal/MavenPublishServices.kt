/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.publish.maven.internal

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.component.ComponentTypeRegistry
import org.gradle.api.publish.maven.internal.dependencies.MavenVersionRangeMapper
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper
import org.gradle.api.publish.maven.internal.publisher.MavenDuplicatePublicationTracker
import org.gradle.api.publish.maven.internal.publisher.MavenPublishers
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.util.internal.BuildCommencedTimeProvider

class MavenPublishServices : AbstractGradleModuleServices() {
    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(ComponentRegistrationAction())
    }

    public override fun registerProjectServices(registration: ServiceRegistration) {
        registration.add(MavenDuplicatePublicationTracker::class.java)
    }

    private class ComponentRegistrationAction : ServiceRegistrationProvider {
        @Provides
        fun configure(registration: ServiceRegistration?, componentTypeRegistry: ComponentTypeRegistry) {
            // TODO There should be a more explicit way to execute an action against existing services
            componentTypeRegistry.maybeRegisterComponentType(MavenModule::class.java)
                .registerArtifactType(MavenPomArtifact::class.java, ArtifactType.MAVEN_POM)
        }

        @Provides
        fun createVersionRangeMapper(versionSelectorScheme: VersionSelectorScheme?): VersionRangeMapper {
            return MavenVersionRangeMapper(versionSelectorScheme)
        }

        @Provides
        fun createMavenPublishers(
            timeProvider: BuildCommencedTimeProvider?,
            repositoryTransportFactory: RepositoryTransportFactory?,
            mavenRepositoryLocator: LocalMavenRepositoryLocator?
        ): MavenPublishers {
            return MavenPublishers(timeProvider, repositoryTransportFactory, mavenRepositoryLocator)
        }
    }
}
