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
/**
 * Tasks for Ivy publishing.
 *
 * @since 1.3
 */
package org.gradle.api.publish.ivy.tasks

import org.gradle.internal.serialization.Transient.Companion.varOf
import org.gradle.internal.serialization.Transient.get
import org.gradle.internal.serialization.Transient.Var.set
import org.gradle.internal.serialization.Cached.get
import org.gradle.api.publish.internal.PublicationInternal.publishableArtifacts
import org.gradle.api.publish.internal.PublicationArtifactSet.files
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.getConfiguredCredentials
import org.gradle.api.publish.internal.PublishOperation.run
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository.getName
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.getUrl
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.isAllowInsecureProtocol
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.repositoryLayout
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.additionalArtifactPatterns
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.additionalIvyPatterns
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.getConfiguredAuthentication
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createIvyRepository
import org.gradle.api.internal.artifacts.repositories.AbstractResolutionAwareArtifactRepository.setName
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.setUrl
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.setAllowInsecureProtocol
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.setConfiguredCredentials
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.authentication

