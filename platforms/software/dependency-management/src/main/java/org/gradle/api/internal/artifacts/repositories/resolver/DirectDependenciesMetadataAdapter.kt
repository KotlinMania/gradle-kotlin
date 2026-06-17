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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

class DirectDependenciesMetadataAdapter(
    attributesFactory: AttributesFactory,
    instantiator: Instantiator,
    dependencyNotationParser: NotationParser<Any, DirectDependencyMetadata>
) : AbstractDependenciesMetadataAdapter<DirectDependencyMetadata?, DirectDependencyMetadataAdapter?>(attributesFactory, instantiator, dependencyNotationParser), DirectDependenciesMetadata {
    override fun adapterImplementationType(): Class<DirectDependencyMetadataAdapter> {
        return DirectDependencyMetadataAdapter::class.java
    }

    override fun getAdapterMetadata(adapter: DirectDependencyMetadataAdapter): ModuleDependencyMetadata {
        return adapter.getMetadata()
    }

    override fun isConstraint(): Boolean {
        return false
    }

    override fun isEndorsingStrictVersions(details: DirectDependencyMetadata): Boolean {
        return details.isEndorsingStrictVersions()
    }
}
