/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName

abstract class AbstractDependencyMetadataConverter(private val excludeRuleConverter: ExcludeRuleConverter) : DependencyMetadataConverter {
    private fun getExtension(artifact: DependencyArtifact): String {
        return (if (artifact.getExtension() != null) artifact.getExtension() else artifact.getType())!!
    }

    protected fun convertExcludeRules(excludeRules: MutableSet<ExcludeRule>): ImmutableList<ExcludeMetadata> {
        if (excludeRules.isEmpty()) {
            return ImmutableList.of<ExcludeMetadata>()
        }
        val builder = ImmutableList.builderWithExpectedSize<ExcludeMetadata>(excludeRules.size)
        for (excludeRule in excludeRules) {
            builder.add(excludeRuleConverter.convertExcludeRule(excludeRule))
        }
        return builder.build()
    }

    protected fun convertArtifacts(dependencyArtifacts: MutableSet<DependencyArtifact>): ImmutableList<IvyArtifactName> {
        if (dependencyArtifacts.isEmpty()) {
            return ImmutableList.of<IvyArtifactName>()
        }
        val names = ImmutableList.builderWithExpectedSize<IvyArtifactName>(dependencyArtifacts.size)
        for (dependencyArtifact in dependencyArtifacts) {
            val name = DefaultIvyArtifactName(dependencyArtifact.getName(), dependencyArtifact.getType(), getExtension(dependencyArtifact), dependencyArtifact.getClassifier())
            names.add(name)
        }
        return names.build()
    }
}
