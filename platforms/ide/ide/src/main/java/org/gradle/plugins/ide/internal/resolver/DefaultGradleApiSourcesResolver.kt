/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.plugins.ide.internal.resolver

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.component.Artifact
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.util.internal.GroovyDependencyUtil
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

class DefaultGradleApiSourcesResolver(private val resolver: ProjectInternal.DetachedResolver) : GradleApiSourcesResolver {
    init {
        addGradleLibsRepository()
    }

    override fun resolveLocalGroovySources(jarName: String): File? {
        val matcher: Matcher = FILE_NAME_PATTERN.matcher(jarName)
        if (!matcher.matches()) {
            return null
        }
        val version = VersionNumber.parse(matcher.group(3))
        val artifactName = matcher.group(1)
        return downloadLocalGroovySources(artifactName, version)
    }

    private fun downloadLocalGroovySources(artifact: String, version: VersionNumber): File? {
        val result: ArtifactResolutionResult = resolver.getDependencies().createArtifactResolutionQuery()
            .forModule(GroovyDependencyUtil.groovyGroupName(version), artifact, version.toString())
            .withArtifacts(JvmLibrary::class.java, mutableListOf<Class<out Artifact?>?>(SourcesArtifact::class.java))
            .execute()

        for (artifactsResult in result.getResolvedComponents()) {
            for (artifactResult in artifactsResult.getArtifacts(SourcesArtifact::class.java)) {
                if (artifactResult is ResolvedArtifactResult) {
                    return artifactResult.getFile()
                }
            }
        }
        return null
    }

    private fun addGradleLibsRepository(): MavenArtifactRepository {
        return resolver.getRepositories().maven(Action { a: MavenArtifactRepository? ->
            a!!.setName("Gradle Libs")
            a.setUrl(gradleLibsRepoUrl())
        })
    }

    companion object {
        private const val GRADLE_LIBS_REPO_URL = "https://repo.gradle.org/gradle/list/libs-releases"
        private const val GRADLE_LIBS_REPO_OVERRIDE_VAR = "GRADLE_LIBS_REPO_OVERRIDE"
        private val FILE_NAME_PATTERN: Pattern = Pattern.compile("(groovy(-.+?)?)-(\\d.+?)\\.jar")

        private fun gradleLibsRepoUrl(): String {
            val repoOverride = System.getenv(GRADLE_LIBS_REPO_OVERRIDE_VAR)
            return if (repoOverride != null) repoOverride else GRADLE_LIBS_REPO_URL
        }
    }
}
