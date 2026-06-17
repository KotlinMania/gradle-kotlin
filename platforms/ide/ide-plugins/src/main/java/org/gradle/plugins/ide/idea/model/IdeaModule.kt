/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Predicate
import com.google.common.collect.Sets
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdeDeprecations.nagDeprecatedProperty
import org.gradle.plugins.ide.internal.IdeDeprecations.nagDeprecatedType
import org.gradle.plugins.ide.internal.resolver.DefaultGradleApiSourcesResolver
import org.gradle.util.internal.ConfigureUtil
import java.io.File
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Enables fine-tuning module details (*.iml file) of the IDEA plugin.
 *
 *
 * Example of use with a blend of most possible properties.
 * Typically you don't have to configure this model directly because Gradle configures it for you.
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'java'
 * id 'idea'
 * }
 *
 * //for the sake of this example, let's introduce a 'performanceTestCompile' configuration
 * configurations {
 * performanceTestCompile
 * performanceTestCompile.extendsFrom(testCompile)
 * }
 *
 * dependencies {
 * //performanceTestCompile "some.interesting:dependency:1.0"
 * }
 *
 * idea {
 *
 * //if you want parts of paths in resulting files (*.iml, etc.) to be replaced by variables (Files)
 * pathVariables GRADLE_HOME: file('~/cool-software/gradle')
 *
 * module {
 * //if for some reason you want to add an extra sourceDirs
 * sourceDirs += file('some-extra-source-folder')
 *
 * //and some extra test source dirs
 * testSources.from(file('some-extra-test-dir'))
 *
 * //and some extra resource dirs
 * resourceDirs += file('some-extra-resource-dir')
 *
 * //and some extra test resource dirs
 * testResources.from(file('some-extra-test-resource-dir'))
 *
 * //and hint to mark some of existing source dirs as generated sources
 * generatedSourceDirs += file('some-extra-source-folder')
 *
 * //and some extra dirs that should be excluded by IDEA
 * excludeDirs += file('some-extra-exclude-dir')
 *
 * //if you don't like the name Gradle has chosen
 * name = 'some-better-name'
 *
 * //if you prefer different output folders
 * inheritOutputDirs = false
 * outputDir = file('muchBetterOutputDir')
 * testOutputDir = file('muchBetterTestOutputDir')
 *
 * //if you prefer different SDK than the one inherited from IDEA project
 * jdkName = '1.6'
 *
 * //put our custom test dependencies onto IDEA's TEST scope
 * scopes.TEST.plus += [ configurations.performanceTestCompile ]
 *
 * //if 'content root' (as IDEA calls it) of the module is different
 * contentRoot = file('my-module-content-root')
 *
 * //if you love browsing Javadoc
 * downloadJavadoc = true
 *
 * //and hate reading sources :)
 * downloadSources = false
 * }
 * }
</pre> *
 *
 * For tackling edge cases, users can perform advanced configuration on the resulting XML file.
 * It is also possible to affect the way the IDEA plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 *
 *
 * beforeMerged and whenMerged closures receive a [Module] parameter
 *
 *
 * Examples of advanced configuration:
 *
 * <pre class='autoTestedWithDeprecations'>
 * plugins {
 * id 'java'
 * id 'idea'
 * }
 *
 * idea {
 * module {
 * iml {
 * //if you like to keep *.iml in a secret folder
 * generateTo = file('secret-modules-folder')
 *
 * //if you want to mess with the resulting XML in whatever way you fancy
 * withXml {
 * def node = it.asNode()
 * node.appendNode('iLoveGradle', 'true')
 * node.appendNode('butAlso', 'I find increasing pleasure tinkering with output *.iml contents. Yeah!!!')
 * }
 *
 * //closure executed after *.iml content is loaded from existing file
 * //but before gradle build information is merged
 * beforeMerged { module -&gt;
 * //if you want skip merging exclude dirs
 * module.excludeFolders.clear()
 * }
 *
 * //closure executed after *.iml content is loaded from existing file
 * //and after gradle build information is merged
 * whenMerged { module -&gt;
 * //you can tinker with [Module]
 * }
 * }
 * }
 * }
 *
</pre> *
 */
abstract class IdeaModule @Inject @Suppress("deprecation") constructor(
    /**
     * An owner of this IDEA module.
     *
     *
     * If IdeaModule requires some information from gradle this field should not be used for this purpose.
     * IdeaModule instances should be configured with all necessary information by the plugin or user.
     */
    val project: Project?, @field:Suppress("deprecation") private val iml: IdeaModuleIml
) {
    /**
     * Configures module name, that is the name of the *.iml file.
     *
     *
     * It's **optional** because the task should configure it correctly for you.
     * By default it will try to use the **project.name** or prefix it with a part of a **project.path** to make
     * sure the module name is unique in the scope of a multi-module build.
     * The 'uniqueness' of a module name is required for correct import into IDEA and the task will make sure the name
     * is unique.
     *
     *
     * **since** 1.0-milestone-2
     *
     *
     * If your project has problems with unique names it is recommended to always run `gradle idea` from the
     * root, i.e. for all subprojects.
     * If you run the generation of the IDEA module only for a single subproject then you may have different results
     * because the unique names are calculated based on IDEA modules that are involved in the specific build run.
     *
     *
     * If you update the module names then make sure you run `gradle idea` from the root, e.g. for all
     * subprojects, including generation of IDEA project.
     * The reason is that there may be subprojects that depend on the subproject with amended module name.
     * So you want them to be generated as well because the module dependencies need to refer to the amended project
     * name.
     * Basically, for non-trivial projects it is recommended to always run `gradle idea` from the root.
     *
     *
     * For example see docs for [IdeaModule]
     */
    var name: String? = null

    /**
     * The directories containing the production sources.
     *
     * For example see docs for [IdeaModule]
     */
    var sourceDirs: MutableSet<File?>? = null

    /**
     * The directories containing the generated sources (both production and test sources).
     *
     *
     * For example see docs for [IdeaModule]
     */
    var generatedSourceDirs: MutableSet<File?>? = java.util.LinkedHashSet<File?>()
    /**
     * The directories containing resources.
     *
     * For example see docs for [IdeaModule]
     *
     * @since 4.7
     */
    /**
     * Sets the directories containing resources.
     *
     * For example see docs for [IdeaModule]
     *
     * @since 4.7
     */
    var resourceDirs: MutableSet<File?>? = java.util.LinkedHashSet<File?>()

    /**
     * The keys of this map are the IDEA scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are collections of [Configuration] objects. The files of the
     * plus configurations are added minus the files from the minus configurations. See example below...
     *
     *
     * Example how to use scopes property to enable 'performanceTestCompile' dependencies in the output *.iml file:
     * <pre class='autoTestedWithDeprecations'>
     * plugins {
     * id 'java'
     * id 'idea'
     * }
     *
     * configurations {
     * performanceTestCompile
     * performanceTestCompile.extendsFrom(testCompile)
     * }
     *
     * dependencies {
     * //performanceTestCompile "some.interesting:dependency:1.0"
     * }
     *
     * idea {
     * module {
     * scopes.TEST.plus += [ configurations.performanceTestCompile ]
     * }
     * }
    </pre> *
     */
    var scopes: MutableMap<String?, MutableMap<String?, MutableCollection<Configuration?>?>?>? = LinkedHashMap<String?, MutableMap<String?, MutableCollection<Configuration?>?>?>()

    /**
     * Whether to download and add sources associated with the dependency jars. Defaults to true.
     *
     * For example see docs for [IdeaModule]
     */
    var isDownloadSources: Boolean = true

    /**
     * Whether to download and add javadoc associated with the dependency jars. Defaults to false.
     *
     * For example see docs for [IdeaModule]
     */
    var isDownloadJavadoc: Boolean = false

    /**
     * The content root directory of the module.
     *
     * For example see docs for [IdeaModule]
     */
    var contentRoot: File? = null

    /**
     * Directories to be excluded.
     *
     * For example see docs for [IdeaModule]
     */
    var excludeDirs: MutableSet<File?>? = null

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by [.getOutputDir] and [.getTestOutputDir].
     *
     *
     * For example see docs for [IdeaModule]
     */
    var inheritOutputDirs: Boolean? = null

    /**
     * The output directory for production classes.
     * If `null`, no entry will be created.
     *
     *
     * For example see docs for [IdeaModule]
     */
    var outputDir: File? = null

    /**
     * The output directory for test classes.
     * If `null`, no entry will be created.
     *
     *
     * For example see docs for [IdeaModule]
     */
    var testOutputDir: File? = null

    /**
     * The variables to be used for replacing absolute paths in the iml entries.
     * For example, you might add a `GRADLE_USER_HOME` variable to point to the Gradle user home dir.
     *
     *
     * For example see docs for [IdeaModule]
     */
    var pathVariables: MutableMap<String?, File?>? = LinkedHashMap<String?, File?>()

    /**
     * The JDK to use for this module.
     * If `null`, the value of the existing or default ipr XML (inherited) is used.
     * If it is set to `inherited`, the project SDK is used.
     * Otherwise the SDK for the corresponding value of java version is used for this module.
     *
     *
     * For example see docs for [IdeaModule]
     */
    var jdkName: String? = null

    /**
     * The module specific language Level to use for this module.
     * When `null`, the module will inherit the language level from the idea project.
     *
     *
     * The Idea module language level is based on the `sourceCompatibility` settings for the associated Gradle project.
     */
    var languageLevel: IdeaLanguageLevel? = null

    /**
     * The module specific bytecode version to use for this module.
     * When `null`, the module will inherit the bytecode version from the idea project.
     *
     *
     * The Idea module bytecode version is based on the `targetCompatibility` settings for the associated Gradle project.
     */
    var targetBytecodeVersion: JavaVersion? = null

    @get:Deprecated("Will be removed in Gradle 10.")
    @set:Deprecated("Will be removed in Gradle 10.")
    var pathFactory: PathFactory? = null
        /**
         * Returns the path factory used to construct paths in the generated *.iml file.
         *
         */
        get() {
            nagDeprecatedProperty(IdeaModule::class.java, "pathFactory")
            return field
        }
        /**
         * Sets the path factory used to construct paths in the generated *.iml file.
         *
         */
        set(pathFactory) {
            nagDeprecatedProperty(IdeaModule::class.java, "pathFactory")
            field = pathFactory
        }

    /**
     * If true then external artifacts (e.g. those found in repositories) will not be included in the resulting classpath (only project and local file dependencies will be included).
     */
    var isOffline: Boolean = false
    var singleEntryLibraries: MutableMap<String?, Iterable<File?>?>? = null

    /**
     * The complete and up-to-date collection of test source directories
     *
     * @return lazily configurable collection of test source directories
     * @since 7.4
     */
    abstract val testSources: ConfigurableFileCollection?

    /**
     * The complete and up-to-date collection of test resource directories.
     *
     * @return lazily configurable collection of test resource directories
     * @since 7.4
     */
    abstract val testResources: ConfigurableFileCollection?

    /**
     * See [.iml]
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun getIml(): IdeaModuleIml {
        nagDeprecatedType(IdeaModuleIml::class.java)
        return iml
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing *.iml content is merged with gradle build information.
     *
     *
     * For example see docs for [IdeaModule].
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun iml(@DelegatesTo(IdeaModuleIml::class) closure: Closure<*>?) {
        ConfigureUtil.configure<IdeaModuleIml?>(closure, getIml())
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing *.iml content is merged with gradle build information.
     *
     *
     * For example see docs for [IdeaModule].
     *
     * @since 3.5
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun iml(action: Action<in IdeaModuleIml?>) {
        nagDeprecatedType(IdeaModuleIml::class.java)
        action.execute(iml)
    }

    var outputFile: File
        /**
         * Configures output *.iml file.
         * It's **optional** because the task should configure it correctly for you (including making sure it is unique in the multi-module build).
         * If you really need to change the output file name (or the module name) it is much easier to do it via the **moduleName** property!
         *
         *
         * Please refer to documentation on **moduleName** property.
         * In IntelliJ IDEA the module name is the same as the name of the *.iml file.
         */
        get() = File(iml.generateTo, this.name + ".iml")
        set(newOutputFile) {
            this.name = newOutputFile.getName().replaceFirst("\\.iml$".toRegex(), "")
            getIml().generateTo = newOutputFile.getParentFile()
        }

    /**
     * Resolves and returns the module's dependencies.
     *
     * @return dependencies
     */
    fun resolveDependencies(): MutableSet<Dependency?> {
        val projectInternal = project as ProjectInternal
        val ideArtifactRegistry = projectInternal.getServices().get<IdeArtifactRegistry?>(IdeArtifactRegistry::class.java)
        val ideaDependenciesProvider = IdeaDependenciesProvider(projectInternal, ideArtifactRegistry, DefaultGradleApiSourcesResolver(projectInternal.newDetachedResolver()))
        return ideaDependenciesProvider.provide(this)
    }

    /**
     * Merges the existing *.iml content with the configuration from this model.
     *
     */
    @Deprecated("Will be removed in Gradle 10.")
    fun mergeXmlModule(xmlModule: Module) {
        whileDisabled(Runnable {
            iml.beforeMerged.execute(xmlModule)
            val contentRoot: Path = this.pathFactory!!.path(this.contentRoot!!)
            val sourceFolders = pathsOf(existing(this.sourceDirs!!))
            val generatedSourceFolders = pathsOf(existing(this.generatedSourceDirs!!))
            val testSourceFolders = pathsOf(existing(this.testSources.getFiles()))
            val resourceFolders = pathsOf(existing(this.resourceDirs!!))
            val testResourceFolders = pathsOf(existing(this.testResources.getFiles()))
            val excludeFolders = pathsOf(this.excludeDirs!!)
            val outputDir: Path? = if (this.outputDir != null) this.pathFactory!!.path(this.outputDir!!) else null
            val testOutputDir: Path? = if (this.testOutputDir != null) this.pathFactory!!.path(this.testOutputDir!!) else null
            val dependencies = resolveDependencies()
            val level = if (this.languageLevel != null) this.languageLevel!!.level else null

            xmlModule.configure(
                contentRoot,
                sourceFolders, testSourceFolders,
                resourceFolders, testResourceFolders,
                generatedSourceFolders,
                excludeFolders,
                this.inheritOutputDirs, outputDir, testOutputDir,
                dependencies,
                this.jdkName!!, level
            )
            iml.whenMerged.execute(xmlModule)
        })
    }

    private fun existing(files: MutableSet<File?>): MutableSet<File?> {
        return Sets.filter<File?>(files, object : Predicate<File?> {
            override fun apply(file: File): Boolean {
                return file.exists()
            }
        })
    }

    private fun pathsOf(files: MutableSet<File?>): MutableSet<Path?> {
        return files.stream().map<FilePath> { file: File? -> this.pathFactory!!.path(file!!) }.collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
    }
}
