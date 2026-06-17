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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

/**
 * A repository of module components.
 *
 * @param <T> the component resolution result type
</T> */
interface ModuleComponentRepository<T> {
    /**
     * A unique identifier for this repository, based on its type and attributes.
     * Two repositories with the same configuration will share the same id.
     * This id is stable across builds on the same machine.
     *
     *
     * The name is not encoded in the id, as it is not relevant for resolution. The name is only used for diagnotics.
     */
    val id: String?

    /**
     * A user-friendly name for this repository.
     */
    val name: String?

    /**
     * Accessor that attempts to locate module components without expensive network operations.
     */
    val localAccess: ModuleComponentRepositoryAccess<T?>?

    /**
     * Accessor that attempts to locate module components remotely, allowing expensive network operations.
     * This access will be disabled when Gradle is executed with `--offline`.
     */
    val remoteAccess: ModuleComponentRepositoryAccess<T?>?

    // TODO - put this somewhere else
    val artifactCache: MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>?

    val componentMetadataSupplier: InstantiatingAction<ComponentMetadataSupplierDetails?>?

    /**
     * Should resolution continue to the next repository if this repository is unavailable due to connection failure.
     *
     *
     * This can only return `true` if the repository is remote.
     *
     * @return `true` if resolution should continue on connection failure, `false` otherwise.
     */
    val isContinueOnConnectionFailure: Boolean

    /**
     * Indicates whether a previous attempt at using this repository for resolution has resulted in disabling it due to unrecoverable errors.
     *
     * @return `true` if the repository is disabled, `false` otherwise.
     */
    val isRepositoryDisabled: Boolean
}
