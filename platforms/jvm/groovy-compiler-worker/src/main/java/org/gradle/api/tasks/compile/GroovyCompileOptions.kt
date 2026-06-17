/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.tasks.compile

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Compilation options to be passed to the Groovy compiler.
 */
abstract class GroovyCompileOptions : Serializable {
    /**
     * Tells whether the compilation task should fail if compile errors occurred. Defaults to `true`.
     */
    /**
     * Sets whether the compilation task should fail if compile errors occurred. Defaults to `true`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isFailOnError: Boolean = true

    /**
     * Tells whether to turn on verbose output. Defaults to `false`.
     */
    /**
     * Sets whether to turn on verbose output. Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isVerbose: Boolean = false

    /**
     * Tells whether to print which source files are to be compiled. Defaults to `false`.
     */
    /**
     * Sets whether to print which source files are to be compiled. Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Console
    var isListFiles: Boolean = false

    /**
     * Tells the source encoding. Defaults to `UTF-8`.
     */
    /**
     * Sets the source encoding. Defaults to `UTF-8`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var encoding: String? = "UTF-8"

    /**
     * Tells whether to run the Groovy compiler in a separate process. Defaults to `true`.
     */
    /**
     * Sets whether to run the Groovy compiler in a separate process. Defaults to `true`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isFork: Boolean = true

    /**
     * Tells whether Java stubs for Groovy classes generated during Java/Groovy joint compilation
     * should be kept after compilation has completed. Useful for joint compilation debugging purposes.
     * Defaults to `false`.
     */
    /**
     * Sets whether Java stubs for Groovy classes generated during Java/Groovy joint compilation
     * should be kept after compilation has completed. Useful for joint compilation debugging purposes.
     * Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isKeepStubs: Boolean = false

    /**
     * Returns the list of acceptable source file extensions. Only takes effect when compiling against
     * Groovy 1.7 or higher. Defaults to `ImmutableList.of("java", "groovy")`.
     */
    /**
     * Sets the list of acceptable source file extensions. Only takes effect when compiling against
     * Groovy 1.7 or higher. Defaults to `ImmutableList.of("java", "groovy")`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var fileExtensions: MutableList<String?>? = ImmutableList.of<String?>("java", "groovy")

    /**
     * Returns optimization options for the Groovy compiler. Allowed values for an option are `true` and `false`.
     * Only takes effect when compiling against Groovy 1.8 or higher.
     *
     *
     * Known options are:
     *
     * <dl>
     * <dt>indy
    </dt> * <dd>Use the invokedynamic bytecode instruction. Requires JDK7 or higher and Groovy 2.0 or higher. Disabled by default.
    </dd> * <dt>int
    </dt> * <dd>Optimize operations on primitive types (e.g. integers). Enabled by default.
    </dd> * <dt>all
    </dt> * <dd>Enable or disable all optimizations. Note that some optimizations might be mutually exclusive.
    </dd></dl> *
     */
    /**
     * Sets optimization options for the Groovy compiler. Allowed values for an option are `true` and `false`.
     * Only takes effect when compiling against Groovy 1.8 or higher.
     */
    @get:Input
    @get:Optional
    @get:ToBeReplacedByLazyProperty
    var optimizationOptions: MutableMap<String?, Boolean?>? = HashMap<String?, Boolean?>()

    /**
     * Sets the directory where Java stubs for Groovy classes will be stored during Java/Groovy joint
     * compilation. Defaults to `null`, in which case a temporary directory will be used.
     */
    /**
     * Returns the directory where Java stubs for Groovy classes will be stored during Java/Groovy joint
     * compilation. Defaults to `null`, in which case a temporary directory will be used.
     */ // TOOD:LPTR Should be just a relative path
    @get:ToBeReplacedByLazyProperty
    @get:Internal
    var stubDir: File? = null

    /**
     * A Groovy script file that configures the compiler, allowing extensive control over how the code is compiled.
     *
     *
     * The script is executed as Groovy code, with the following context:
     *
     *
     *  * The instance of [CompilerConfiguration](https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/CompilerConfiguration.html) available as the `configuration` variable.
     *  * All static members of [CompilerCustomizationBuilder](https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/customizers/builder/CompilerCustomizationBuilder.html) pre imported.
     *
     *
     *
     * This facilitates the following pattern:
     *
     * <pre>
     * withConfig(configuration) {
     * // use compiler configuration DSL here
     * }
    </pre> *
     *
     *
     * For example, to activate type checking for all Groovy classes…
     *
     * <pre>
     * import groovy.transform.TypeChecked
     *
     * withConfig(configuration) {
     * ast(TypeChecked)
     * }
    </pre> *
     *
     *
     * Please see [the Groovy compiler customization builder documentation](https://docs.groovy-lang.org/latest/html/documentation/#compilation-customizers)
     * for more information about the compiler configuration DSL.
     *
     *
     *
     * **This feature is only available if compiling with Groovy 2.1 or later.**
     *
     *
     * @see [CompilerConfiguration](https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/CompilerConfiguration.html)
     *
     * @see [CompilerCustomizationBuilder](https://docs.groovy-lang.org/latest/html/gapi/org/codehaus/groovy/control/customizers/builder/CompilerCustomizationBuilder.html)
     */
    /**
     * Sets the path to the groovy configuration file.
     *
     * @see .getConfigurationScript
     */
    @get:ToBeReplacedByLazyProperty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var configurationScript: File? = null

    /**
     * Whether the Groovy code should be subject to Java annotation processing.
     *
     *
     * Annotation processing of Groovy code works by having annotation processors visit the Java stubs generated by the
     * Groovy compiler in order to support joint compilation of Groovy and Java source.
     *
     *
     * When set to `true`, stubs will be unconditionally generated for all Groovy sources, and Java annotations processors will be executed on those stubs.
     *
     *
     * When this option is set to `false` (the default), Groovy code will not be subject to annotation processing, but any joint compiled Java code will be.
     * If the compiler argument `"-proc:none"` was specified as part of the Java compile options, the value of this flag will be ignored.
     * No annotation processing will be performed regardless, on Java or Groovy source.
     */
    /**
     * Sets whether Java annotation processors should process annotations on stubs.
     *
     * Defaults to `false`.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isJavaAnnotationProcessing: Boolean = false

    /**
     * Whether the Groovy compiler generate metadata for reflection on method parameter names on JDK 8 and above.
     *
     * @since 6.1
     */
    /**
     * Sets whether metadata for reflection on method parameter names should be generated.
     * Defaults to `false`
     *
     * @since 6.1
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isParameters: Boolean = false

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Nested
    abstract val forkOptions: GroovyForkOptions?

    /**
     * Execute the given action against [.getForkOptions].
     *
     * @since 8.11
     */
    fun forkOptions(action: Action<in GroovyForkOptions?>) {
        action.execute(this.forkOptions)
    }

    @JvmField
    @get:Input
    abstract val disabledGlobalASTTransformations: SetProperty<String?>?

    companion object {
        private const val serialVersionUID: Long = 0
    }
}
