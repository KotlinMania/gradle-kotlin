/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Provides the component that owns the root variant of a resolved configuration within a project.
 *
 *
 * TODO #1629: This should be replaced with [AdhocRootComponentProvider] and
 * all resolved configurations should live within an adhoc root component.
 */
@ServiceScope(Scope.Project::class)
class ProjectRootComponentProvider(// Services
    private val owner: ProjectState,
    private val componentIdentity: DependencyMetaDataProvider,
    private val schema: AttributesSchemaInternal,
    private val configurationsProvider: ConfigurationsProvider,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val localResolveStateFactory: LocalComponentGraphResolveStateFactory,
    private val attributesSchemaFactory: ImmutableAttributesSchemaFactory,
    private val adhocRootComponentProvider: AdhocRootComponentProvider
) : RootComponentProvider {
    // State
    private var cachedValue: LocalComponentGraphResolveState? = null

    override fun getRootComponent(detached: Boolean): LocalComponentGraphResolveState {
        if (detached) {
            return adhocRootComponentProvider.getRootComponent(true)
        }

        if (cachedValue == null) {
            this.cachedValue = createProjectRootComponent()
        }

        return cachedValue!!
    }

    private fun createProjectRootComponent(): LocalComponentGraphResolveState {
        val module = componentIdentity.module

        val moduleVersionId = moduleIdentifierFactory.moduleWithVersion(module.group!!, module.name!!, module.version!!)
        val componentIdentifier: ComponentIdentifier = owner.getComponentIdentifier()
        val status: String = module.status!!
        val immutableSchema = attributesSchemaFactory.create(schema)

        val metadata = LocalComponentGraphResolveMetadata(
            moduleVersionId!!,
            componentIdentifier,
            status,
            immutableSchema
        )

        return localResolveStateFactory.stateFor(owner, metadata, configurationsProvider)
    }
}
