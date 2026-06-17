/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.component

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure
import org.gradle.internal.deprecation.DeprecationLogger.deprecateType

/**
 * This type is `deprecated` and will be removed in Gradle 10.
 *
 *
 * This is used by the oss-licenses plugin that is used by Now In Android.  To remove this class, we must first
 * address that with a new version of oss-licenses, or the removal of it from NiA.
 * See [this PR](https://github.com/google/play-services-plugins/pull/305).
 *
 *
 * This is temporarily available for migration only.
 * This exception class is internal and has been replaced by [ArtifactSelectionException], which is also internal.
 * If possible, catch a [RuntimeException] instead to avoid depending on Gradle internal classes.
 */
@Deprecated("")
abstract class AmbiguousVariantSelectionException(message: String) : ArtifactSelectionException(message, EMPTY_RESOLUTION_FAILURE, mutableListOf<String>()) {
    init {
        deprecateType(AmbiguousVariantSelectionException::class.java)
            .withAdvice("The " + AmbiguousVariantSelectionException::class.java.getName() + " type is temporarily available for migration only.")!!
            .willBeRemovedInGradle10()
            .withUserManual("feature_lifecycle", "sec:internal")!!
            .nagUser()
    }

    companion object {
        private val EMPTY_RESOLUTION_FAILURE: ArtifactSelectionFailure = object : ArtifactSelectionFailure {
            override fun getTargetComponent(): ComponentIdentifier {
                return ComponentIdentifier { "empty component" }
            }

            override fun getTargetVariant(): String {
                return "empty variant"
            }

            override fun getRequestedAttributes(): ImmutableAttributes {
                return ImmutableAttributes.EMPTY
            }

            override fun describeRequestTarget(): String {
                return "empty target"
            }

            override fun getProblemId(): ResolutionFailureProblemId {
                return ResolutionFailureProblemId.UNKNOWN_RESOLUTION_FAILURE
            }
        }
    }
}
