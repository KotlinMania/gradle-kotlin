/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.publish.maven.internal.validation

import com.google.common.base.Strings
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishException
import org.gradle.api.publish.internal.validation.PublicationErrorChecker
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet
import org.jspecify.annotations.NullMarked
import java.nio.file.Path
import java.util.EnumSet
import java.util.Map
import java.util.function.Function
import java.util.function.ToIntFunction
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Static util class containing publication checks specific to Maven publications.
 */
@NullMarked
object MavenPublicationErrorChecker : PublicationErrorChecker() {
    /**
     * When the artifacts declared in a component are modified for publishing (name/classifier/extension), then the
     * Maven publication no longer represents the underlying java component. Instead of
     * publishing incorrect metadata, we fail any attempt to publish the module metadata.
     *
     *
     * In the long term, we will likely prevent any modification of artifacts added from a component. Instead, we will
     * make it easier to modify the component(s) produced by a project, allowing the
     * published metadata to accurately reflect the local component metadata.
     *
     *
     * Should only be called after publication is populated from the component.
     *
     * @param projectDisplayName the [project display name][org.gradle.api.Project.getDisplayName]
     * @param buildDir absolute directory that is the base of the build
     * @param componentName name of the component
     * @param source original artifact
     * @param mainArtifacts set of published artifacts to verify
     * @throws PublishException if the artifacts are modified
     */
    fun checkThatArtifactIsPublishedUnmodified(
        projectDisplayName: String, buildDir: Path, componentName: String,
        source: PublishArtifact, mainArtifacts: DefaultMavenArtifactSet
    ) {
        // Note: this just verifies that no component artifact has been removed. Additional artifacts are allowed.
        val differences: MutableMap<MavenArtifact, MutableSet<ArtifactDifference>> = HashMap<MavenArtifact, MutableSet<ArtifactDifference>>()
        for (mavenArtifact in mainArtifacts) {
            val differenceSet = EnumSet.noneOf<ArtifactDifference>(ArtifactDifference::class.java)
            if (source.getFile() != mavenArtifact.file) {
                differenceSet.add(ArtifactDifference.FILE)
            }
            // Necessary as the classifier can be converted from an empty string to null
            if (Strings.nullToEmpty(source.getClassifier()) != Strings.nullToEmpty(mavenArtifact.getClassifier())) {
                differenceSet.add(ArtifactDifference.CLASSIFIER)
            }
            if (source.getExtension() != mavenArtifact.getExtension()) {
                differenceSet.add(ArtifactDifference.EXTENSION)
            }
            // If it's all equal, we found a matching artifact that is being published
            if (differenceSet.isEmpty()) {
                return
            }
            differences.put(mavenArtifact, differenceSet)
        }
        throw PublishException(
            "Cannot publish module metadata because an artifact from the '" + componentName +
                    "' component has been removed. The available artifacts had these problems:\n" + formatDifferences(projectDisplayName, buildDir, source, differences)
        )
    }

    private val DIFFERENCE_SET_COMPARATOR: Comparator<MutableSet<ArtifactDifference>> =  // Put the artifacts with the least differences first, since they're more likely to be useful
        Comparator.comparingInt<MutableSet<ArtifactDifference>>(ToIntFunction { obj: MutableSet<ArtifactDifference> -> obj.size }) // Prefer FILE differences over CLASSIFIER differences over EXTENSION differences,
            // since different classifiers/extensions are unlikely to be right
            .thenComparing<Boolean>(Function { set: MutableSet<ArtifactDifference> -> set.contains(ArtifactDifference.FILE) })
            .thenComparing<Boolean>(Function { set: MutableSet<ArtifactDifference> -> set.contains(ArtifactDifference.CLASSIFIER) })
            .thenComparing<Boolean>(Function { set: MutableSet<ArtifactDifference> -> set.contains(ArtifactDifference.EXTENSION) })

    private val DIFFERENCE_ENTRY_COMPARATOR: Comparator<MutableMap.MutableEntry<MavenArtifact, MutableSet<ArtifactDifference>>> =
        Map.Entry.comparingByValue<MavenArtifact, MutableSet<ArtifactDifference>>(DIFFERENCE_SET_COMPARATOR) // Last ditch effort to make the order deterministic
            .thenComparing<Any>(Function { entry: MutableMap.MutableEntry<MavenArtifact, MutableSet<ArtifactDifference>> -> entry.key.file.toPath() })

    private fun formatDifferences(projectDisplayName: String, buildDir: Path, source: PublishArtifact, differencesByArtifact: MutableMap<MavenArtifact, MutableSet<ArtifactDifference>>): String {
        val differencesFormatted = differencesByArtifact.entries.stream()
            .sorted(DIFFERENCE_ENTRY_COMPARATOR)
            .limit(3).map<String> { entry: MutableMap.MutableEntry<MavenArtifact, MutableSet<ArtifactDifference>>? ->
                val artifact = entry!!.key
                val differenceSet = entry.value
                val artifactPath = buildDir.relativize(artifact.file.toPath())
                "- " + artifactPath + ":\n" + formatDifferenceSet(projectDisplayName, buildDir, source, artifact, differenceSet)
            }
        val warningForNonPrintedArtifacts = if (differencesByArtifact.size > 3)
            Stream.of<String>("... (" + (differencesByArtifact.size - 3) + " more artifact(s) not shown)")
        else
            Stream.empty<String>()
        return Stream.concat<String>(differencesFormatted, warningForNonPrintedArtifacts)
            .collect(Collectors.joining("\n", "", "\n"))
    }

    private fun formatDifferenceSet(projectDisplayName: String, buildDir: Path, expected: PublishArtifact, actual: MavenArtifact, differenceSet: MutableSet<ArtifactDifference>): String {
        return differenceSet.stream().map<String> { diff: ArtifactDifference? ->
            when (diff) {
                ArtifactDifference.FILE -> {
                    val expectedFile = buildDir.relativize(expected.getFile().toPath())
                    val actualFile = buildDir.relativize(actual.file.toPath())
                    return@map "\t- file differs (relative to " + projectDisplayName + "): (expected) " + expectedFile + " != (actual) " + actualFile
                }

                ArtifactDifference.CLASSIFIER -> return@map "\t- classifier differs: (expected) " + expected.getClassifier() + " != (actual) " + actual.getClassifier()
                ArtifactDifference.EXTENSION -> return@map "\t- extension differs: (expected) " + expected.getExtension() + " != (actual) " + actual.getExtension()
                else -> throw IllegalArgumentException("Unknown difference: " + diff)
            }
        }.collect(Collectors.joining("\n"))
    }

    private enum class ArtifactDifference {
        FILE,
        CLASSIFIER,
        EXTENSION,
    }
}
