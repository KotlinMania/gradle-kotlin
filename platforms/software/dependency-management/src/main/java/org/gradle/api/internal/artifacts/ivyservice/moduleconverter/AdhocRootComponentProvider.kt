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

import org.gradle.api.internal.artifacts.AnonymousModule
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Creates an adhoc root components, which is intended to own the root variant of a dependency graph.
 *
 *
 * Adhoc root components are not cacheable, have no unique identity, and contain no variants.
 * They are purely meant to own the root variant of a dependency graph.
 */
@ServiceScope(Scope.Project::class)
class AdhocRootComponentProvider(
    private val schema: AttributesSchemaInternal,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val attributesSchemaFactory: ImmutableAttributesSchemaFactory,
    private val localResolveStateFactory: LocalComponentGraphResolveStateFactory
) : RootComponentProvider {
    override fun getRootComponent(detached: Boolean): LocalComponentGraphResolveState {
        val module: Module = AnonymousModule()

        val status: String = module.status!!
        val moduleVersionId = moduleIdentifierFactory.moduleWithVersion(module.group!!, module.name!!, module.version!!)

        val immutableSchema = attributesSchemaFactory.create(schema)

        return localResolveStateFactory.adhocRootComponentState(
            status,
            moduleVersionId!!,
            immutableSchema
        )
    }
}
