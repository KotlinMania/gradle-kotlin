/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.internal.component.resolution.failure.type.AbstractVariantSelectionByAttributesFailure.getRequestedAttributes
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory.createArtifactResolutionQuery
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry.artifactTypeContainer
import org.gradle.api.internal.artifacts.VariantTransformRegistry.registerTransform
import org.gradle.util.internal.CollectionUtils.flattenCollections
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.addFailureDescriber
import org.gradle.internal.component.resolution.failure.type.AbstractVariantSelectionByAttributesFailure.describeRequestTarget
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber.buildResolutions
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber.getDocumentationRegistry
import org.gradle.api.internal.DocumentationRegistry.getDocumentationFor
import org.gradle.api.JavaVersion.majorVersion
import org.gradle.api.JavaVersion.Companion.current

