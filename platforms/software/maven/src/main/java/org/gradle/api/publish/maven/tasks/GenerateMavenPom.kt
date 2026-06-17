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
package org.gradle.api.publish.maven.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.serialization.Cached
import org.gradle.internal.serialization.Transient.Companion.varOf
import java.io.File
import javax.inject.Inject

/**
 * Generates a Maven module descriptor (POM) file.
 *
 * @since 1.4
 */
@UntrackedTask(because = "Gradle doesn't understand the data structures used to configure this task")
abstract class GenerateMavenPom : DefaultTask() {
    private val pom = varOf<MavenPom?>()
    private var destination: Any? = null
    private val mavenPomSpec = Cached.of({ MavenPomFileGenerator.generateSpec(getPom() as MavenPomInternal?) }
    )

    @get:Inject
    protected abstract val fileResolver: FileResolver?

    /**
     * The Maven POM.
     *
     * @return The Maven POM.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun getPom(): MavenPom? {
        return pom.get()
    }

    fun setPom(pom: MavenPom?) {
        this.pom.set(pom)
    }

    /**
     * The file the POM will be written to.
     *
     * @return The file the POM will be written to
     */
    @OutputFile
    @ToBeReplacedByLazyProperty
    fun getDestination(): File? {
        return if (destination == null) null else this.fileResolver.resolve(destination)
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * @param destination The file the descriptor will be written to.
     * @since 4.0
     */
    fun setDestination(destination: File?) {
        this.destination = destination
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * The value is resolved with [Project.file]
     *
     * @param destination The file the descriptor will be written to.
     */
    fun setDestination(destination: Any?) {
        this.destination = destination
    }

    @TaskAction
    fun doGenerate() {
        mavenPomSpec.get()!!.writeTo(getDestination())
    }
}
