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
package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.result.UnresolvedArtifactResult
import org.gradle.api.component.Artifact

class DefaultUnresolvedArtifactResult(private val identifier: ComponentArtifactIdentifier, private val type: Class<out Artifact>, private val failure: Throwable) : UnresolvedArtifactResult {
    override fun toString(): String {
        return identifier.getDisplayName()
    }

    override fun getId(): ComponentArtifactIdentifier {
        return identifier
    }

    override fun getType(): Class<out Artifact> {
        return type
    }

    override fun getFailure(): Throwable {
        return failure
    }
}
