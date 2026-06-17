/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resolve

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.internal.logging.text.TreeFormatter
import java.util.function.Consumer
import kotlin.math.min

class ModuleVersionNotFoundException : ModuleVersionResolveException, ResolutionProvider {
    private var resolutions: MutableList<String?> = ImmutableList.of<String?>()

    /**
     * This is used by [withIncomingPaths].
     */
    constructor(selector: ComponentSelector?, message: Factory<String?>?, resolutions: MutableCollection<String?>) : super(selector, message) {
        this.resolutions = ImmutableList.copyOf<String?>(resolutions)
    }


    constructor(
        selector: ModuleComponentSelector,
        attemptedLocations: MutableCollection<String>,
        unmatchedVersions: MutableCollection<String>,
        rejectedVersions: MutableCollection<RejectedVersion?>
    ) : super(selector, format(selector, attemptedLocations, unmatchedVersions, rejectedVersions)) {
        recordPossibleResolution(attemptedLocations)
    }

    constructor(id: ModuleVersionIdentifier, attemptedLocations: MutableCollection<String>) : super(id, format(id, attemptedLocations)) {
        recordPossibleResolution(attemptedLocations)
    }

    constructor(selector: ModuleComponentSelector?, attemptedLocations: MutableCollection<String>) : super(selector, format(selector, attemptedLocations)) {
        recordPossibleResolution(attemptedLocations)
    }

    /**
     * This method should ideally use more data to figure out if the message should be displayed
     * or not. In particular, the ivy patterns can make it difficult to find out if an Ivy artifact
     * source should be configured. At this stage, this information is lost, so we do a best effort
     * based on the file locations.
     */
    private fun recordPossibleResolution(locations: MutableCollection<String>) {
        if (locations.size == 1) {
            val singleLocation = locations.iterator().next()
            val format: String? = getFormatName(singleLocation)
            if (format != null) {
                resolutions = ImmutableList.of<String?>(
                    String.format(
                        "If the artifact you are trying to retrieve can be found in the repository but without metadata in '%s' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.",
                        format
                    )
                )
            }
        }
    }

    public override fun getResolutions(): MutableList<String?> {
        return resolutions
    }

    override fun createCopy(): ModuleVersionResolveException {
        try {
            val message = getMessage()
            return javaClass.getConstructor(ComponentSelector::class.java, Factory::class.java, MutableCollection::class.java)
                .newInstance(getSelector(), org.gradle.internal.Factory { message } as Factory<String?>, resolutions)
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }
    }

    companion object {
        private fun format(
            selector: ModuleComponentSelector,
            locations: MutableCollection<String>,
            unmatchedVersions: MutableCollection<String>,
            rejectedVersions: MutableCollection<RejectedVersion?>
        ): Factory<String?> {
            return org.gradle.internal.Factory {
                val builder = TreeFormatter()
                if (unmatchedVersions.isEmpty() && rejectedVersions.isEmpty()) {
                    builder.node(String.format("Could not find any matches for %s as no versions of %s:%s are available.", selector, selector.getGroup(), selector.getModule()))
                } else {
                    builder.node(String.format("Could not find any version that matches %s.", selector))
                    if (!unmatchedVersions.isEmpty()) {
                        builder.node("Versions that do not match")
                        appendSizeLimited(builder, unmatchedVersions)
                    }
                    if (!rejectedVersions.isEmpty()) {
                        val byRule: MutableCollection<RejectedVersion> = ArrayList<RejectedVersion>(rejectedVersions.size)
                        val byAttributes: MutableCollection<RejectedVersion> = ArrayList<RejectedVersion>(rejectedVersions.size)
                        mapRejections(rejectedVersions, byRule, byAttributes)
                        if (!byRule.isEmpty()) {
                            builder.node("Versions rejected by component selection rules")
                            appendSizeLimited(builder, byRule)
                        }
                        if (!byAttributes.isEmpty()) {
                            builder.node("Versions rejected by attribute matching")
                            appendSizeLimited(builder, byAttributes)
                        }
                    }
                }
                addLocations(builder, locations)
                builder.toString()
            }
        }

        private fun mapRejections(`in`: MutableCollection<RejectedVersion?>, outByRule: MutableCollection<RejectedVersion>, outByAttributes: MutableCollection<RejectedVersion>) {
            for (version in `in`) {
                if (version is RejectedByAttributesVersion) {
                    outByAttributes.add(version)
                } else {
                    outByRule.add(version!!)
                }
            }
        }

        private fun format(id: ModuleVersionIdentifier?, locations: MutableCollection<String>): Factory<String?> {
            return org.gradle.internal.Factory {
                val builder = TreeFormatter()
                builder.node(String.format("Could not find %s.", id))
                addLocations(builder, locations)
                builder.toString()
            }
        }

        private fun format(selector: ModuleComponentSelector?, locations: MutableCollection<String>): Factory<String?> {
            return org.gradle.internal.Factory {
                val builder = TreeFormatter()
                builder.node(String.format("Could not find any version that matches %s.", selector))
                addLocations(builder, locations)
                builder.toString()
            }
        }

        private fun appendSizeLimited(builder: TreeFormatter, values: MutableCollection<*>) {
            builder.startChildren()
            val iterator: MutableIterator<*> = values.iterator()
            val count = min(5, values.size)
            for (i in 0..<count) {
                val next: Any = iterator.next()!!
                if (next is RejectedVersion) {
                    next.describeTo(builder)
                } else {
                    builder.node(next.toString())
                }
            }
            if (count < values.size) {
                builder.node(String.format("+ %d more", values.size - count))
            }
            builder.endChildren()
        }

        private fun addLocations(builder: TreeFormatter, locations: MutableCollection<String>) {
            if (locations.isEmpty()) {
                return
            }
            builder.node("Searched in the following locations")
            builder.startChildren()

            locations.forEach(Consumer { text: String? -> builder.node(text) })

            builder.endChildren()
        }

        private fun getFormatName(singleLocation: String): String? {
            val isPom = singleLocation.endsWith(".pom")
            val isIvy = singleLocation.contains("ivy-") && singleLocation.endsWith(".xml")
            val isModule = singleLocation.endsWith(".module")
            return if (isPom) "Maven POM" else (if (isIvy) "ivy.xml" else (if (isModule) "Gradle module" else null))
        }
    }
}
