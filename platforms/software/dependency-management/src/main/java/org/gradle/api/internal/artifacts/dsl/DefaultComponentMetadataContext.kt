/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata

internal class DefaultComponentMetadataContext(
    private val details: ComponentMetadataDetails, // We keep this field for access from Groovy scripts, as we currently miss some public API: https://github.com/gradle/gradle/issues/12349
    private val metadata: ModuleComponentResolveMetadata
) : ComponentMetadataContext {
    private val descriptorFactory: MetadataDescriptorFactory

    init {
        this.descriptorFactory = MetadataDescriptorFactory(metadata)
    }

    override fun <T> getDescriptor(descriptorClass: Class<T?>): T? {
        return descriptorFactory.createDescriptor<T?>(descriptorClass)
    }

    override fun getDetails(): ComponentMetadataDetails {
        return details
    }
}
