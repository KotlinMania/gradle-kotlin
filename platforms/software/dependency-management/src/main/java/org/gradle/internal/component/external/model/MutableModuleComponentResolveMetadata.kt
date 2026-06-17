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
package org.gradle.internal.component.external.model

import org.gradle.api.internal.attributes.ImmutableAttributes

interface MutableModuleComponentResolveMetadata {
    /**
     * The identifier for this component
     */
    /**
     * Sets the component id and legacy module version id
     */
    var id: ModuleComponentIdentifier?

    /**
     * The module version associated with this module.
     */
    val moduleVersionId: ModuleVersionIdentifier?

    /**
     * Creates an immutable copy of this meta-data.
     */
    fun asImmutable(): ModuleComponentResolveMetadata?

    var isMissing: Boolean

    var isChanging: Boolean

    var status: String?

    var statusScheme: MutableList<String>?

    var sources: MutableModuleSources?

    fun addVariant(variant: MutableComponentVariant): MutableComponentVariant?

    fun addVariant(variantName: String, attributes: ImmutableAttributes): MutableComponentVariant?

    var attributes: AttributeContainer?

    var isExternalVariant: Boolean

    var isComponentMetadataRuleCachingEnabled: Boolean

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    fun artifact(type: String, extension: String?, classifier: String?): ModuleComponentArtifactMetadata?

    val attributesFactory: AttributesFactory?

    /**
     * Returns the metadata rules container for this module
     */
    val variantMetadataRules: VariantMetadataRules?

    /**
     * Declares that this component belongs to a virtual platform.
     * @param platform the identifier of the virtual platform
     */
    fun belongsTo(platform: VirtualComponentIdentifier)

    val platformOwners: MutableSet<out VirtualComponentIdentifier>?

    val mutableVariants: MutableList<out MutableComponentVariant>?
}
