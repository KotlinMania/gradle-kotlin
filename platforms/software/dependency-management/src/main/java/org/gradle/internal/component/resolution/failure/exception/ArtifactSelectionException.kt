/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.resolution.failure.exception

import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure

/**
 * Represents a failure during variant selection when an artifact variant of a component cannot be selected
 * from the result of [Variant Selection][org.gradle.internal.component.resolution.failure.interfaces] by the [AttributeMatchingArtifactVariantSelector].
 *
 * Note: Temporarily non-`final`, so long as [org.gradle.internal.component.AmbiguousVariantSelectionException] is not yet removed.
 */
@Suppress("deprecation")
open class ArtifactSelectionException : AbstractResolutionFailureException {
    constructor(message: String, failure: ArtifactSelectionFailure, resolutions: MutableList<String>) : super(message, failure, resolutions)

    constructor(message: String, failure: ArtifactSelectionFailure, resolutions: MutableList<String>, cause: Throwable) : super(message, failure, resolutions, cause)

    override fun getFailure(): ArtifactSelectionFailure {
        return failure as ArtifactSelectionFailure
    }
}
