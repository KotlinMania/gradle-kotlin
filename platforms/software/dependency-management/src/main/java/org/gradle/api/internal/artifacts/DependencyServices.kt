/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractGradleModuleServices

class DependencyServices : AbstractGradleModuleServices() {
    public override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(DependencyManagementGlobalScopeServices())
    }

    public override fun registerGradleUserHomeServices(registration: ServiceRegistration) {
        registration.addProvider(DependencyManagementGradleUserHomeScopeServices())
    }

    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(DependencyManagementBuildScopeServices())
    }

    public override fun registerProjectServices(registration: ServiceRegistration) {
        registration.addProvider(DependencyManagementProjectScopeServices())
    }

    public override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.addProvider(DependencyManagementBuildSessionScopeServices())
    }

    public override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.add(ArtifactSetToFileCollectionFactory::class.java)
        registration.addProvider(DependencyManagementBuildTreeScopeServices())
    }
}
