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
package org.gradle.api.internal.artifacts.repositories

import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.internal.Actions
import java.util.function.Supplier

internal class DefaultMavenRepositoryContentDescriptor(repositoryNameSupplier: Supplier<String>, versionParser: VersionParser) :
    DefaultRepositoryContentDescriptor(repositoryNameSupplier, versionParser), MavenRepositoryContentDescriptor {
    private var snapshots = true
    private var releases = true

    override fun releasesOnly() {
        snapshots = false
        releases = true
    }

    override fun snapshotsOnly() {
        snapshots = true
        releases = false
    }

    override fun toContentFilter(): Action<in ArtifactResolutionDetails> {
        val filter = super.toContentFilter()
        if (!snapshots || !releases) {
            val action: Action<in ArtifactResolutionDetails> = Action { details: ArtifactResolutionDetails ->
                if (!details.isVersionListing()) {
                    val version = MavenVersionUtils.toEffectiveVersion(details.getComponentId()!!.getVersion())
                    if (snapshots && !version.endsWith("-SNAPSHOT")) {
                        details.notFound()
                        return@Action
                    }
                    if (releases && version.endsWith("-SNAPSHOT")) {
                        details.notFound()
                    }
                }
            }
            if (filter === Actions.doNothing<Any>()) {
                return action
            }
            return Actions.composite<ArtifactResolutionDetails>(filter, action)
        }
        return filter
    }

    override fun asMutableCopy(): RepositoryContentDescriptorInternal {
        val copy = DefaultMavenRepositoryContentDescriptor(getRepositoryNameSupplier(), getVersionParser())
        if (getIncludedConfigurations() != null) {
            copy.setIncludedConfigurations(Sets.newHashSet<String>(getIncludedConfigurations()))
        }
        if (getExcludedConfigurations() != null) {
            copy.setExcludedConfigurations(Sets.newHashSet<String>(getExcludedConfigurations()))
        }
        if (getIncludeSpecs() != null) {
            copy.setIncludeSpecs(Sets.newHashSet<ContentSpec>(getIncludeSpecs()))
        }
        if (getExcludeSpecs() != null) {
            copy.setExcludeSpecs(Sets.newHashSet<ContentSpec>(getExcludeSpecs()))
        }
        if (getRequiredAttributes() != null) {
            copy.setRequiredAttributes(Maps.newHashMap<Attribute<Any>, MutableSet<Any>>(getRequiredAttributes()!!))
        }
        copy.releases = releases
        copy.snapshots = snapshots
        return copy
    }
}
