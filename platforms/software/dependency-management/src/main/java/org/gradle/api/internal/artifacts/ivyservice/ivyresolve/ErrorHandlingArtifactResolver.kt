/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult

class ErrorHandlingArtifactResolver(private val resolver: ArtifactResolver) : ArtifactResolver {
    override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
        try {
            resolver.resolveArtifactsWithType(component, artifactType, result)
        } catch (t: Exception) {
            result.failed(ArtifactResolveException(component.getId()!!, t))
        }
    }

    override fun resolveArtifact(component: ComponentArtifactResolveMetadata, artifact: ComponentArtifactMetadata, result: BuildableArtifactResolveResult) {
        try {
            resolver.resolveArtifact(component, artifact, result)
        } catch (t: Exception) {
            result.failed(ArtifactResolveException(artifact.getId()!!, t))
        }
    }
}
