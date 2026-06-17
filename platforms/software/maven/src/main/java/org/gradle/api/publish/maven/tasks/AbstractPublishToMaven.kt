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
package org.gradle.api.publish.maven.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.serialization.Transient.Companion.varOf
import org.gradle.work.DisableCachingByDefault
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Base class for tasks that publish a [MavenPublication].
 *
 * @since 2.4
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractPublishToMaven : DefaultTask() {
    private val publication = varOf<MavenPublicationInternal?>()

    init {
        // Allow the publication to participate in incremental build
        getInputs().files(Callable {
            val publicationInternal = this.publicationInternal
            if (publicationInternal == null) null else publicationInternal.publishableArtifacts!!.files
        } as Callable<FileCollection?>)
            .withPropertyName("publication.publishableFiles")
            .withPathSensitivity(PathSensitivity.NAME_ONLY)

        // Should repositories be able to participate in incremental?
        // At the least, they may be able to express themselves as output files
        // They *might* have input files and other dependencies as well though
        // Inputs: The credentials they need may be expressed in a file
        // Dependencies: Can't think of a case here
    }

    /**
     * The publication to be published.
     *
     * @return The publication to be published
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun getPublication(): MavenPublication? {
        return publication.get()
    }

    /**
     * Sets the publication to be published.
     *
     * @param publication The publication to be published
     */
    fun setPublication(publication: MavenPublication?) {
        this.publication.set(toPublicationInternal(publication))
    }

    @get:Internal
    protected val publicationInternal: MavenPublicationInternal?
        get() = toPublicationInternal(getPublication())

    @get:Inject
    protected abstract val mavenPublishers: MavenPublishers?

    @get:Inject
    protected abstract val duplicatePublicationTracker: MavenDuplicatePublicationTracker?

    companion object {
        private fun toPublicationInternal(publication: MavenPublication?): MavenPublicationInternal? {
            if (publication == null) {
                return null
            } else if (publication is MavenPublicationInternal) {
                return publication
            } else {
                throw InvalidUserDataException(
                    String.format(
                        "publication objects must implement the '%s' interface, implementation '%s' does not",
                        MavenPublicationInternal::class.java.getName(),
                        publication.javaClass.getName()
                    )
                )
            }
        }
    }
}
