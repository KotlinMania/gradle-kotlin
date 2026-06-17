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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.DisplayName
import org.gradle.internal.exceptions.DefaultMultiCauseException.getResolutions
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Adds additional context to exceptions thrown during resolution.
 */
@ServiceScope(Scope.Project::class)
class ResolveExceptionMapper(
    private val domainObjectContext: DomainObjectContext,
    private val documentationRegistry: DocumentationRegistry
) {
    fun mapFailures(failures: MutableCollection<Throwable>, type: String, contextDisplayName: DisplayName): TypedResolveException? {
        if (failures.isEmpty()) {
            return null
        }

        val displayName = contextDisplayName.getDisplayName()
        if (failures.size > 1) {
            return TypedResolveException(
                type,
                displayName,
                failures.stream().map<Throwable> { failure: Throwable? -> mapRepositoryOverrideFailure(displayName, failure!!) }.collect(ImmutableList.toImmutableList<Throwable>())
            )
        }

        val failure = failures.iterator().next()
        return mapFailure(failure, type, displayName)
    }

    fun mapFailure(failure: Throwable, type: String, contextDisplayName: String): TypedResolveException {
        if (failure !is TypedResolveException) {
            return TypedResolveException(
                type,
                contextDisplayName,
                ImmutableList.of<Throwable>(mapRepositoryOverrideFailure(contextDisplayName, failure))
            )
        }

        val resolveException = failure

        val mappedCauses: MutableList<Throwable> = resolveException.getCauses().stream()
            .map<Throwable> { cause: Throwable? -> mapRepositoryOverrideFailure(contextDisplayName, cause!!) }
            .collect(ImmutableList.toImmutableList<Throwable>())

        // Keep the original exception if no changes were made to
        // the causes to avoid losing the original stack trace
        if (mappedCauses == resolveException.getCauses()) {
            return resolveException
        }

        return TypedResolveException(resolveException.type, contextDisplayName, mappedCauses, resolveException.getResolutions())
    }

    // TODO: We should handle this exception at the source instead of using instanceof to detect it after it is thrown.
    //       We should try to avoid catching and analyzing runtime exceptions
    fun mapRepositoryOverrideFailure(contextDisplayName: String, failure: Throwable): Throwable {
        if (failure !is ModuleVersionNotFoundException || !settingsRepositoriesIgnored()) {
            return failure
        }

        val resolutions = ImmutableList.of<String>(
            "The project declares repositories, effectively ignoring the repositories you have declared in the settings.\n" +
                    "To determine how project repositories are declared, configure your build to fail on project repositories.\n" +
                    documentationRegistry.getDocumentationRecommendationFor("information", "declaring_repositories", "sub:fail_build_on_project_repositories")
        )

        return TypedResolveException(
            "dependencies",
            contextDisplayName,
            mutableSetOf<Throwable>(failure),
            resolutions
        )
    }

    private fun settingsRepositoriesIgnored(): Boolean {
        if (domainObjectContext !is ProjectInternal) {
            return false
        }

        val project = domainObjectContext

        val hasSettingsRepos: Boolean
        try {
            hasSettingsRepos = !project.getGradle().getSettings().getDependencyResolutionManagement().getRepositories().isEmpty()
        } catch (e: Throwable) {
            // To catch `The settings are not yet available for` error
            return false
        }

        val hasProjectRepos = !project.getRepositories().isEmpty()
        return hasProjectRepos && hasSettingsRepos
    }
}
