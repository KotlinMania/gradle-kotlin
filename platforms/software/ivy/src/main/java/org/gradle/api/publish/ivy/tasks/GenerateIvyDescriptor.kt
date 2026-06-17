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
package org.gradle.api.publish.ivy.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal
import org.gradle.api.publish.ivy.internal.tasks.IvyDescriptorFileGenerator
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
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
@UntrackedTask(because = "Gradle doesn't understand the data structures")
abstract class GenerateIvyDescriptor : DefaultTask() {
    private val descriptor = varOf<IvyModuleDescriptorSpec?>()
    private val ivyDescriptorSpec = Cached.of({ this.computeIvyDescriptorFileSpec() })

    private var destination: Any? = null

    @get:Inject
    protected abstract val fileResolver: PathToFileResolver?

    /**
     * The module descriptor metadata.
     *
     * @return The module descriptor.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    fun getDescriptor(): IvyModuleDescriptorSpec? {
        return descriptor.get()
    }

    fun setDescriptor(descriptor: IvyModuleDescriptorSpec?) {
        this.descriptor.set(descriptor)
    }

    /**
     * The file the descriptor will be written to.
     *
     * @return The file the descriptor will be written to
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
        ivyDescriptorSpec.get()!!.writeTo(getDestination()!!)
    }

    fun computeIvyDescriptorFileSpec(): IvyDescriptorFileGenerator.DescriptorFileSpec {
        val descriptorInternal: IvyModuleDescriptorSpecInternal = toIvyModuleDescriptorInternal(getDescriptor())
        return IvyDescriptorFileGenerator.generateSpec(descriptorInternal)
    }

    companion object {
        private fun toIvyModuleDescriptorInternal(ivyModuleDescriptorSpec: IvyModuleDescriptorSpec?): IvyModuleDescriptorSpecInternal {
            if (ivyModuleDescriptorSpec == null) {
                return null
            } else if (ivyModuleDescriptorSpec is IvyModuleDescriptorSpecInternal) {
                return ivyModuleDescriptorSpec
            } else {
                throw InvalidUserDataException(
                    String.format(
                        "ivyModuleDescriptor implementations must implement the '%s' interface, implementation '%s' does not",
                        IvyModuleDescriptorSpecInternal::class.java.getName(),
                        ivyModuleDescriptorSpec.javaClass.getName()
                    )
                )
            }
        }
    }
}
