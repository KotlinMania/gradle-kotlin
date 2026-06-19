/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.converters.BasicGradleProjectIdentifierMixin
import org.gradle.tooling.internal.consumer.converters.BuildEnvironmentVersionInfoMixin
import org.gradle.tooling.internal.consumer.converters.EclipseExternalDependencyUnresolvedMixin
import org.gradle.tooling.internal.consumer.converters.EclipseProjectHasAutoBuildMixin
import org.gradle.tooling.internal.consumer.converters.FixedBuildIdentifierProvider
import org.gradle.tooling.internal.consumer.converters.GradleProjectIdentifierMixin
import org.gradle.tooling.internal.consumer.converters.IdeaModuleDependencyTargetNameMixin
import org.gradle.tooling.internal.consumer.converters.IdeaProjectJavaLanguageSettingsMixin
import org.gradle.tooling.internal.consumer.converters.IncludedBuildsMixin
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaProject

open class HasCompatibilityMapping(private val versionDetails: VersionDetails) {
    fun <T> applyCompatibilityMapping(viewBuilder: ViewBuilder<T?>, parameters: ConsumerOperationParameters): ViewBuilder<T?> {
        val projectIdentifier = DefaultProjectIdentifier(parameters.projectDir, ":")
        return applyCompatibilityMapping<T?>(viewBuilder, projectIdentifier)
    }

    fun <T> applyCompatibilityMapping(viewBuilder: ViewBuilder<T?>, projectIdentifier: DefaultProjectIdentifier): ViewBuilder<T?> {
        viewBuilder.mixInTo(GradleProject::class.java, GradleProjectIdentifierMixin(projectIdentifier.buildIdentifier))
        viewBuilder.mixInTo(BasicGradleProject::class.java, BasicGradleProjectIdentifierMixin(projectIdentifier.buildIdentifier))
        val identifierProvider = FixedBuildIdentifierProvider(projectIdentifier)
        identifierProvider.applyTo<T?>(viewBuilder)
        viewBuilder.mixInTo(IdeaProject::class.java, IdeaProjectJavaLanguageSettingsMixin::class.java)
        viewBuilder.mixInTo(IdeaDependency::class.java, IdeaModuleDependencyTargetNameMixin::class.java)
        viewBuilder.mixInTo(GradleBuild::class.java, IncludedBuildsMixin::class.java)
        viewBuilder.mixInTo(EclipseProject::class.java, EclipseProjectHasAutoBuildMixin::class.java)
        viewBuilder.mixInTo(EclipseExternalDependency::class.java, EclipseExternalDependencyUnresolvedMixin::class.java)
        viewBuilder.mixInTo(BuildEnvironment::class.java, BuildEnvironmentVersionInfoMixin(versionDetails.version!!))
        return viewBuilder
    }
}
