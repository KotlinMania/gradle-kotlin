/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ExternalResourceName
import java.net.URI

class IvyResourcePattern : AbstractResourcePattern, ResourcePattern {
    constructor(pattern: String) : super(pattern)

    constructor(baseUri: URI, pattern: String) : super(baseUri, pattern)

    override fun toString(): String {
        return "Ivy pattern '" + getPattern() + "'"
    }

    override fun getLocation(artifact: ModuleComponentArtifactMetadata): ExternalResourceName {
        val attributes = toAttributes(artifact)
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes))
    }

    override fun toVersionListPattern(module: ModuleIdentifier, artifact: IvyArtifactName): ExternalResourceName {
        val attributes = toAttributes(module, artifact)
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes))
    }

    override fun toModulePath(module: ModuleIdentifier): ExternalResourceName? {
        throw UnsupportedOperationException()
    }

    override fun toModuleVersionPath(componentIdentifier: ModuleComponentIdentifier): ExternalResourceName {
        val attributes = ImmutableMap.of<String, String>(
            "organisation", componentIdentifier.getGroup(),
            "module", componentIdentifier.getModule(),
            "artifact", componentIdentifier.getModule(),
            "revision", componentIdentifier.getVersion()
        )
        val resolve = getBase().getRoot().resolve(substituteTokens(this.pathWithoutArtifactPart, attributes))
        return resolve
    }

    protected val pathWithoutArtifactPart: String
        get() {
            val path = getBase().getPath()
            var i = path.lastIndexOf('/')
            if (i > 0) {
                i = path.indexOf("/[artifact]", i)
            }
            if (i < 0) {
                throw UnsupportedOperationException("Cannot locate module version for non standard Ivy layout.")
            }
            return path.substring(0, i)
        }
}
