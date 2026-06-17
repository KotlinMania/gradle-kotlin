/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.publish.tasks

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.Publication
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.metadata.GradleModuleMetadataWriter
import org.gradle.api.publish.internal.metadata.InvalidPublicationChecker
import org.gradle.api.publish.internal.metadata.ModuleMetadataSpec
import org.gradle.api.publish.internal.metadata.ModuleMetadataSpecBuilder
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.Try
import org.gradle.internal.serialization.Cached
import org.gradle.internal.serialization.Transient
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/**
 * Generates a Gradle metadata file to represent a published [SoftwareComponent] instance.
 *
 * @since 4.3
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateModuleMetadata : DefaultTask() {
    private val publication: Transient<Property<Publication?>?>
    private val publications: Transient<ListProperty<Publication?>?>

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    val artifacts: FileCollection
    private val inputState = Cached.of({ this.computeInputState() })

    init {
        val objectFactory = this.objectFactory
        this.publication = Transient.of(objectFactory.property<T?>(Publication::class.java))
        this.publications = Transient.of(objectFactory.listProperty<T?>(Publication::class.java))

        this.artifacts = this.fileCollectionFactory.create(GenerateModuleMetadata.VariantFiles(this.taskDependencyFactory))

        this.suppressedValidationErrors.convention(mutableSetOf<String?>())

        // TODO - should be incremental
        getOutputs().upToDateWhen(Specs.satisfyNone<Task?>())
        setOnlyIf("The publication is attached to a component", SerializableLambdas.spec<Task?>(SerializableLambdas.SerializableSpec { task: Task? -> hasAttachedComponent() }))
    }

    // TODO - this should be an input
    /**
     * Returns the publication to generate the metadata file for.
     */
    @Internal
    fun getPublication(): Property<Publication?>? {
        return publication.get()
    }

    // TODO - this should be an input
    /**
     * Returns the publications of the current project, used in generation to connect the modules of a component together.
     *
     * @since 4.4
     */
    @Internal
    fun getPublications(): ListProperty<Publication?>? {
        return publications.get()
    }

    @get:Inject
    protected abstract val fileCollectionFactory: FileCollectionFactory?

    @get:Inject
    protected abstract val buildInvocationScopeId: BuildInvocationScopeId?

    @get:Inject
    protected abstract val projectDependencyPublicationResolver: ProjectDependencyPublicationResolver?

    @get:Inject
    protected abstract val checksumService: ChecksumService?

    @get:OutputFile
    abstract val outputFile: RegularFileProperty?

    @get:Input
    abstract val suppressedValidationErrors: SetProperty<String?>?

    @TaskAction
    fun run() {
        val inputState = inputState()
        check(inputState is InputState.Ready) { inputState.toString() }
        writeModuleMetadata(
            inputState.moduleMetadataSpec.get()
        )
    }

    private fun writeModuleMetadata(moduleMetadataSpec: ModuleMetadataSpec?) {
        val outputFile = this.outputFile.get()
        try {
            bufferedWriterFor(outputFile.getAsFile()).use { writer ->
                moduleMetadataWriter().writeTo(writer, moduleMetadataSpec)
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Could not generate metadata file " + outputFile, e)
        }
    }

    @Throws(FileNotFoundException::class)
    private fun bufferedWriterFor(file: File): BufferedWriter {
        return BufferedWriter(OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8))
    }

    private fun moduleMetadataWriter(): GradleModuleMetadataWriter {
        return GradleModuleMetadataWriter(this.buildInvocationScopeId, this.checksumService)
    }

    private fun hasAttachedComponent(): Boolean {
        val inputState = inputState()
        if (inputState is InputState.ComponentMissing) {
            val publicationName = inputState.publicationName
            getLogger().warn(
                publicationName + " isn't attached to a component. Gradle metadata only supports publications with software components (e.g. from component.java)"
            )
            return false
        }
        return true
    }

    private fun computeInputState(): InputState {
        return if (component() == null)
            InputState.ComponentMissing(publicationName())
        else
            InputState.Ready(moduleMetadataSpec())
    }

    private fun moduleMetadataSpec(): Try<ModuleMetadataSpec?> {
        return Try.ofFailable({ this.computeModuleMetadataSpec() })
    }

    private fun computeModuleMetadataSpec(): ModuleMetadataSpec {
        val publication = publication()
        val checker = InvalidPublicationChecker(publication.getName(), getPath(), this.suppressedValidationErrors.get())
        val spec = ModuleMetadataSpecBuilder(
            publication,
            publications(),
            checker,
            this.dependencyCoordinateResolverFactory
        ).build().get()
        checker.validate()
        return spec
    }

    internal open class InputState {
        internal class Ready(val moduleMetadataSpec: Try<ModuleMetadataSpec?>) : InputState()

        internal class ComponentMissing(val publicationName: String?) : InputState()
    }

    private inner class VariantFiles(private val taskDependencyFactory: TaskDependencyFactory) : MinimalFileSet, Buildable {
        override fun getDisplayName(): String {
            return "files of " + this@GenerateModuleMetadata.getPath()
        }

        override fun getBuildDependencies(): TaskDependency {
            val dependency = taskDependencyFactory.configurableDependency()
            val component = component()
            if (component != null) {
                forEachArtifactOf(component, Action { values: PublishArtifact? -> dependency.add(values) })
            }
            return dependency
        }

        override fun getFiles(): MutableSet<File?> {
            val component = component()
            return if (component == null) ImmutableSet.of<File?>() else filesOf(component)
        }

        fun filesOf(component: SoftwareComponentInternal): MutableSet<File?> {
            val files: MutableSet<File?> = LinkedHashSet<File?>()
            forEachArtifactOf(component, Action { artifact: PublishArtifact? -> files.add(artifact!!.getFile()) })
            return files
        }

        fun forEachArtifactOf(component: SoftwareComponentInternal, action: Action<PublishArtifact?>) {
            for (variant in component.getUsages()) {
                for (publishArtifact in variant.getArtifacts()) {
                    action.execute(publishArtifact)
                }
            }
        }
    }

    private fun inputState(): InputState {
        return this.inputState.get()!!
    }

    private fun publicationName(): String {
        return publication().displayName.toString()
    }

    private fun component(): SoftwareComponentInternal {
        return publication().component.getOrNull()
    }

    private fun publication(): PublicationInternal<*> {
        return uncheckedNonnullCast<PublicationInternal<*>?>(publication.get()!!.get())!!
    }

    private fun publications(): MutableList<PublicationInternal<*>?> {
        return uncheckedCast<MutableList<PublicationInternal<*>?>?>(publications.get()!!.get())!!
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory

    @get:Inject
    protected abstract val dependencyCoordinateResolverFactory: DependencyCoordinateResolverFactory?

    @get:Inject
    protected abstract val taskDependencyFactory: TaskDependencyFactory?
}
