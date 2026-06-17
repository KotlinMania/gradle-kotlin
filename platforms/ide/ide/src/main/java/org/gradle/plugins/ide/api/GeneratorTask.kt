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
package org.gradle.plugins.ide.api

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.MutableActionSet
import org.gradle.plugins.ide.internal.generator.generator.Generator
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 *
 * A `GeneratorTask` generates a configuration file based on a domain object of type T.
 * When executed the task:
 *
 *
 *  * loads the object from the input file, if it exists.
 *
 *  * Calls the beforeConfigured actions, passing the object to each action.
 *
 *  * Configures the object in some task-specific way.
 *
 *  * Calls the afterConfigured actions, passing the object to each action.
 *
 *  * writes the object to the output file.
 *
 *
 *
 * @param <T> The domain object for the configuration file.
</T> */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class GeneratorTask<T> : ConventionTask() {
    /**
     * Sets the input file to load the initial configuration from.
     *
     * @param inputFile The input file. Use null to use the output file.
     */
    @get:Internal("Covered by inputFileIfExists")
    open var inputFile: File? = null
        /**
         * The input file to load the initial configuration from. Defaults to the output file. If the specified input file
         * does not exist, this task uses some default initial configuration.
         *
         * @return The input file.
         */
        get() = if (field != null) field else this.outputFile
    /**
     * The output file to write the final configuration to.
     *
     * @return The output file.
     */
    /**
     * Sets the output file to write the final configuration to.
     *
     * @param outputFile The output file.
     */
    @get:OutputFile
    open var outputFile: File? = null
    protected val beforeConfigured: MutableActionSet<T?> = MutableActionSet<T?>()
    protected val afterConfigured: MutableActionSet<T?> = MutableActionSet<T?>()
    protected var generator: Generator<T?>? = null

    protected var domainObject: T? = null

    init {
        if (!this.incremental) {
            getOutputs().upToDateWhen(Specs.satisfyNone<Task?>())
        }
    }

    @get:Internal
    protected open val incremental: Boolean
        /**
         * Whether this generator task can be treated as an incremental task or not
         *
         * @since 4.7
         */
        get() = false

    @TaskAction
    protected open fun generate() {
        val inputFile = this.inputFileIfExists
        if (inputFile != null) {
            try {
                domainObject = generator!!.read(inputFile)
            } catch (e: RuntimeException) {
                throw GradleException(
                    String.format(
                        "Cannot parse file '%s'.\n"
                                + "       Perhaps this file was tinkered with? In that case try delete this file and then retry.",
                        inputFile
                    ), e
                )
            }
        } else {
            domainObject = generator!!.defaultInstance()
        }
        beforeConfigured.execute(domainObject)
        generator!!.configure(domainObject)
        afterConfigured.execute(domainObject)

        generator!!.write(domainObject, this.outputFile)
    }

    @get:Inject
    protected abstract val instantiator: Instantiator?

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    protected val inputFileIfExists: File?
        // Workaround for when the task is given an input file that doesn't exist
        get() {
            val inputFile = this.inputFile
            if (inputFile != null && inputFile.exists()) {
                return inputFile
            } else {
                return null
            }
        }
}
