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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.repositories.PatternHelper
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ExternalResourceName
import java.net.URI

class M2ResourcePattern : AbstractResourcePattern {
    constructor(pattern: String) : super(pattern)

    constructor(baseUri: URI, pattern: String) : super(baseUri, pattern)

    override fun toString(): String {
        return "M2 pattern '" + getPattern() + "'"
    }

    override fun getLocation(artifact: ModuleComponentArtifactMetadata): ExternalResourceName {
        val attributes = toAttributes(artifact)
        val pattern = maybeSubstituteTimestamp(artifact, getBase().getPath())
        return getBase().getRoot().resolve(substituteTokens(pattern, attributes))
    }

    private fun maybeSubstituteTimestamp(artifact: ModuleComponentArtifactMetadata, pattern: String): String {
        var pattern = pattern
        if (artifact.getComponentId() is MavenUniqueSnapshotComponentIdentifier) {
            val snapshotId = artifact.getComponentId() as MavenUniqueSnapshotComponentIdentifier?
            pattern = pattern
                .replaceFirst("-\\[revision]".toRegex(), "-" + snapshotId!!.getTimestampedVersion())
                .replace("[revision]", snapshotId.getSnapshotVersion())
        }
        return pattern
    }

    override fun toVersionListPattern(module: ModuleIdentifier, artifact: IvyArtifactName): ExternalResourceName {
        val attributes = toAttributes(module, artifact)
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes))
    }

    override fun toModulePath(module: ModuleIdentifier): ExternalResourceName {
        val pattern = getBase().getPath()
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw UnsupportedOperationException("Cannot locate module for non-maven layout.")
        }
        val metaDataPattern = pattern.substring(0, pattern.length - MavenPattern.M2_PER_MODULE_PATTERN.length - 1)
        return getBase().getRoot().resolve(substituteTokens(metaDataPattern, toAttributes(module)))
    }

    override fun toModuleVersionPath(componentIdentifier: ModuleComponentIdentifier): ExternalResourceName {
        val pattern = getBase().getPath()
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw UnsupportedOperationException("Cannot locate module version for non-maven layout.")
        }
        val metaDataPattern = pattern.substring(0, pattern.length - MavenPattern.M2_PER_MODULE_VERSION_PATTERN.length - 1)
        return getBase().getRoot().resolve(substituteTokens(metaDataPattern, toAttributes(componentIdentifier)))
    }

    override fun substituteTokens(pattern: String, attributes: MutableMap<String, String>): String {
        val org = attributes.get(PatternHelper.ORGANISATION_KEY)
        if (org != null) {
            attributes.put(org.gradle.api.internal.artifacts.repositories.PatternHelper.ORGANISATION_KEY, org.replace(".", "/"))
        }
        return super.substituteTokens(pattern, attributes)
    }
}
