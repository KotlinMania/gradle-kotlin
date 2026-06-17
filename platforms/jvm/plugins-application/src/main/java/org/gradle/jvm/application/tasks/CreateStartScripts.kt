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
package org.gradle.jvm.application.tasks

import com.google.common.collect.Lists
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.plugins.MainClass
import org.gradle.api.internal.plugins.MainModule
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.internal.plugins.UnixStartScriptGenerator
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.jvm.application.scripts.ScriptGenerator
import org.gradle.util.internal.GUtil
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.LinkedList
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Creates start scripts for launching JVM applications.
 *
 *
 * Example:
 * <pre class='autoTested'>
 * task createStartScripts(type: CreateStartScripts) {
 * outputDir = file('build/sample')
 * mainClass = 'org.gradle.test.Main'
 * applicationName = 'myApp'
 * classpath = files('path/to/some.jar')
 * }
</pre> *
 *
 *
 * Note: the Gradle `"application"` plugin adds a pre-configured task of this type named `"startScripts"`.
 *
 *
 * The task generates separate scripts targeted at Microsoft Windows environments and UNIX-like environments (e.g. Linux, macOS).
 * The actual generation is implemented by the [.getWindowsStartScriptGenerator] and [.getUnixStartScriptGenerator] properties, of type [ScriptGenerator].
 *
 *
 * Example:
 * <pre class='autoTested'>
 * task createStartScripts(type: CreateStartScripts) {
 * unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
 * windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
 * }
 *
 * class CustomUnixStartScriptGenerator implements ScriptGenerator {
 * void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 * // implementation
 * }
 * }
 *
 * class CustomWindowsStartScriptGenerator implements ScriptGenerator {
 * void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 * // implementation
 * }
 * }
</pre> *
 *
 *
 * The default generators are of the type [TemplateBasedScriptGenerator], with default templates.
 * This templates can be changed via the [TemplateBasedScriptGenerator.setTemplate] method.
 *
 *
 * The default implementations used by this task use [Groovy's SimpleTemplateEngine](https://docs.groovy-lang.org/latest/html/documentation/template-engines.html#_simpletemplateengine)
 * to parse the template, with the following variables available:
 *
 *  * `applicationName` - See [JavaAppStartScriptGenerationDetails.getApplicationName].
 *  * `gitRef` - See [JavaAppStartScriptGenerationDetails.getGitRef].
 *  * `optsEnvironmentVar` - See [JavaAppStartScriptGenerationDetails.getOptsEnvironmentVar].
 *  * `exitEnvironmentVar` - See [JavaAppStartScriptGenerationDetails.getExitEnvironmentVar].
 *  * `moduleEntryPoint` - The module entry point, or `null` if none. Will also include the main class name if present, in the form `[moduleName]/[className]`.
 *  * `mainClassName` - The main class name, or usually `""` if none. For legacy reasons, this may be set to `--module [moduleEntryPoint]` when using a main module.
 * This behavior should not be relied upon and may be removed in a future release.
 *  * `entryPointArgs` - The arguments to be used on the command-line to enter the application, as a joined string. It should be inserted before the program arguments.
 *  * `defaultJvmOpts` - See [JavaAppStartScriptGenerationDetails.getDefaultJvmOpts].
 *  * `appNameSystemProperty` - See [JavaAppStartScriptGenerationDetails.getAppNameSystemProperty].
 *  * `appHomeRelativePath` - The path, relative to the script's own path, of the app home.
 *  * `classpath` - See [JavaAppStartScriptGenerationDetails.getClasspath]. It is already encoded as a joined string.
 *  * `modulePath` (different capitalization) - See [JavaAppStartScriptGenerationDetails.getModulePath]. It is already encoded as a joined string.
 *
 *
 *
 * The encoded paths expect a variable named `APP_HOME` to be present in the script, set to the application home directory which can be resolved using `appHomeRelativePath`.
 *
 *
 *
 * Example:
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 * unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 * windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
</pre> *
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CreateStartScripts : ConventionTask() {
    /**
     * The directory to write the scripts into.
     */
    @get:ToBeReplacedByLazyProperty
    @get:OutputDirectory
    var outputDir: File? = null
    /**
     * The directory to write the scripts into in the distribution.
     *
     * @since 4.5
     */
    /**
     * The directory to write the scripts into in the distribution.
     *
     * @since 4.5
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var executableDir: String? = "bin"

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var defaultJvmOpts: Iterable<String?>? = LinkedList<String?>()

    /**
     * The application's name.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var applicationName: String? = null

    @get:ToBeReplacedByLazyProperty
    @get:Input
    @get:Optional
    var optsEnvironmentVar: String? = null
        /**
         * The environment variable to use to provide additional options to the JVM.
         */
        get() {
            if (GUtil.isTrue(field)) {
                return field
            }

            if (!GUtil.isTrue(this.applicationName)) {
                return null
            }

            return GUtil.toConstant(this.applicationName) + "_OPTS"
        }
    private var exitEnvironmentVar: String? = null

    /**
     * The class path for the application.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Optional
    @get:Classpath
    var classpath: FileCollection? = null

    /**
     * Returns the module path handling for executing the main class.
     *
     * @since 6.4
     */
    @get:Nested
    val modularity: ModularitySpec

    /**
     * The UNIX-like start script generator.
     *
     *
     * Defaults to an implementation of [TemplateBasedScriptGenerator].
     */
    @get:Nested
    var unixStartScriptGenerator: ScriptGenerator? = UnixStartScriptGenerator()

    /**
     * The Windows start script generator.
     *
     *
     * Defaults to an implementation of [TemplateBasedScriptGenerator].
     */
    @get:Nested
    var windowsStartScriptGenerator: ScriptGenerator? = WindowsStartScriptGenerator()

    init {
        this.gitRef.convention("HEAD")
        this.modularity = this.objectFactory.newInstance<DefaultModularitySpec>(DefaultModularitySpec::class.java)
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Inject
    protected abstract val javaModuleDetector: JavaModuleDetector

    /**
     * The environment variable to use to control exit value (Windows only).
     *
     */
    @Optional
    @Input
    @Deprecated("No longer used in the default start script templates. Will be removed in Gradle 10.")
    fun getExitEnvironmentVar(): String? {
        DeprecationLogger.deprecateMethod(CreateStartScripts::class.java, "getExitEnvironmentVar()")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_exit_environment_var")!!
            .nagUser()
        return computeExitEnvironmentVar()
    }

    private fun computeExitEnvironmentVar(): String? {
        if (GUtil.isTrue(exitEnvironmentVar)) {
            return exitEnvironmentVar
        }

        if (!GUtil.isTrue(this.applicationName)) {
            return null
        }

        return GUtil.toConstant(this.applicationName) + "_EXIT_CONSOLE"
    }

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    val unixScript: File
        /**
         * Returns the full path to the Unix script. The target directory is represented by the output directory, the file name is the application name without a file extension.
         */
        get() = File(this.outputDir, this.applicationName)

    @get:ToBeReplacedByLazyProperty
    @get:Internal
    val windowsScript: File
        /**
         * Returns the full path to the Windows script. The target directory is represented by the output directory, the file name is the application name plus the file extension .bat.
         */
        get() = File(this.outputDir, this.applicationName + ".bat")

    @get:Input
    @get:Optional
    abstract val mainModule: Property<String?>?

    @get:Input
    @get:Optional
    abstract val mainClass: Property<String?>?

    @get:Input
    @get:Optional
    @get:Incubating
    abstract val gitRef: Property<String?>?

    @Deprecated("")
    fun setExitEnvironmentVar(exitEnvironmentVar: String?) {
        DeprecationLogger.deprecateMethod(CreateStartScripts::class.java, "setExitEnvironmentVar(String)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_exit_environment_var")!!
            .nagUser()
        this.exitEnvironmentVar = exitEnvironmentVar
    }

    @TaskAction
    fun generate() {
        val generator = StartScriptGenerator(unixStartScriptGenerator!!, windowsStartScriptGenerator!!)
        val javaModuleDetector: JavaModuleDetector = this.javaModuleDetector
        generator.setApplicationName(this.applicationName!!)
        generator.setGitRef(this.gitRef.get())
        generator.setEntryPoint(this.entryPoint)
        generator.setDefaultJvmOpts(this.defaultJvmOpts)
        generator.setOptsEnvironmentVar(this.optsEnvironmentVar!!)
        // Skipping use of getExitEnvironmentVar() to avoid deprecation warning
        generator.setExitEnvironmentVar(computeExitEnvironmentVar()!!)
        generator.setClasspath(getRelativePath(javaModuleDetector.inferClasspath(this.mainModule.isPresent(), this.classpath)))
        generator.setModulePath(getRelativePath(javaModuleDetector.inferModulePath(this.mainModule.isPresent(), this.classpath)))
        if (StringUtils.isEmpty(this.executableDir)) {
            generator.setScriptRelPath(this.unixScript.getName())
        } else {
            generator.setScriptRelPath(this.executableDir + "/" + this.unixScript.getName())
        }
        generator.generateUnixScript(this.unixScript)
        generator.generateWindowsScript(this.windowsScript)
    }

    private val entryPoint: AppEntryPoint
        get() {
            if (this.mainModule.isPresent()) {
                return MainModule(this.mainModule.get(), this.mainClass.getOrNull())
            }
            return MainClass(this.mainClass.getOrElse(""))
        }

    @get:ToBeReplacedByLazyProperty(unreported = true, comment = "Skipped for report since method is protected")
    @get:Input
    protected val relativeClasspath: Iterable<String?>?
        get() {
            //a list instance is needed here, as org.gradle.internal.snapshot.ValueSnapshotter.processValue() does not support
            //serializing Iterators directly
            val classpathNullable = this.classpath
            if (classpathNullable == null) {
                return mutableListOf<String?>()
            }
            return getRelativePath(classpathNullable)
        }

    private fun getRelativePath(path: FileCollection): Iterable<String?> {
        return path.getFiles().stream().map<String?> { input: File? -> "lib/" + input!!.getName() }.collect(Collectors.toCollection(Supplier { Lists.newArrayList() }))
    }
}
