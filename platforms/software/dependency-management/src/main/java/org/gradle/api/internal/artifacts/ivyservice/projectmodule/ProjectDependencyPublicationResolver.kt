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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.gradle.util.Path

/**
 * Given project coordinates, and optionally the name of a variant in that project,
 * determine the coordinates which should be used in other published metadata to reference
 * the project component or its variant.
 *
 * TODO: This data should instead be exposed by dependency-management via a ResolutionResult.
 */
interface ProjectDependencyPublicationResolver {
    /**
     * Determines the coordinates of the given type for the root component of the
     * project identified by `identityPath`.
     *
     * @throws UnsupportedOperationException if the project cannot be resolved.
     */
    fun <T> resolveComponent(coordsType: Class<T?>, identityPath: Path): T?

    /**
     * Determines the coordinates of the given type that should be used to reference the
     * variant with name `variantName`, contained in the root component of the
     * project identified by `identityPath`.
     *
     * @return null if the `variantName` does not exist in the target project.
     */
    fun <T> resolveVariant(coordsType: Class<T?>, identityPath: Path, variantName: String): T?
}
