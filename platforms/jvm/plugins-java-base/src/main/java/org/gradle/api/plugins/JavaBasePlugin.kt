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
package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs
import org.gradle.api.internal.tasks.compile.JavaCompileExecutableUtils
import org.gradle.api.internal.tasks.testing.TestExecutableUtils
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.internal.ReportUtilities
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.internal.JavaExecExecutableUtils
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.javadoc.internal.JavadocExecutableUtils
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.Cast
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JavadocTool
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.lang.Boolean
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Supplier
import javax.inject.Inject
import kotlin.Deprecated
import kotlin.String
import kotlin.Suppress
import kotlin.toString

/**
 *
 * A [Plugin] which compiles and tests Java source, and assembles it into a JAR file.
 *
 * This plugin is automatically applied to most projects that build any JVM language source.  It creates a [JavaPluginExtension]
 * extension named `java` that is used to configure all jvm-related components in the project.
 *
 * It is responsible for configuring the conventions of any [SourceSet]s that are present and used by
 * (for example) the Java, Groovy, or Kotlin plugins.
 *
 * @see [Java plugin reference](https://docs.gradle.org/current/userguide/java_plugin.html)
 */
abstract class JavaBasePlugin @Inject constructor(private val jvmPluginServices: JvmPluginServices, private val propertyFactory: PropertyFactory) : Plugin<Project?> {
    private val javaClasspathPackaging: Boolean

    init {
        this.javaClasspathPackaging = Boolean.getBoolean(COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY)
    }

    @get:Inject
    protected abstract val jvmLanguageUtils: JvmLanguageUtilities?

    override fun apply(project: Project) {
        project.getPluginManager().apply(BasePlugin::class.java)
        project.getPluginManager().apply(JvmEcosystemPlugin::class.java)
        project.getPluginManager().apply(ReportingBasePlugin::class.java)
        project.getPluginManager().apply(JvmToolchainsPlugin::class.java)

        val javaPluginExtension: DefaultJavaPluginExtension = addExtensions(project)

        configureCompileDefaults(project, javaPluginExtension)
        configureSourceSetDefaults(project, javaPluginExtension)
        configureJavaDoc(project, javaPluginExtension)

        configureTest(project, javaPluginExtension)
        configureBuildNeeded(project)
        configureBuildDependents(project)
        configureArchiveDefaults(project)
        configureJavaExecTasks(project)
    }

    private fun configureSourceSetDefaults(project: Project, javaPluginExtension: JavaPluginExtension) {
        javaPluginExtension.getSourceSets().all(Action { sourceSet: SourceSet? ->
            val configurations = project.getConfigurations()
            defineConfigurationsForSourceSet(sourceSet!!, configurations as RoleBasedConfigurationContainerInternal)
            Companion.definePathsForSourceSet(sourceSet, project)

            createProcessResourcesTask(sourceSet, sourceSet.resources!!, project)
            val compileTask: TaskProvider<JavaCompile?> = createCompileJavaTask(sourceSet, sourceSet.java!!, project)
            createClassesTask(sourceSet, project)

            configureLibraryElements(compileTask, sourceSet, configurations)
            configureTargetPlatform(compileTask, sourceSet, configurations)
        })
    }

    private fun configureLibraryElements(compileJava: TaskProvider<JavaCompile?>, sourceSet: SourceSet, configurations: ConfigurationContainer) {
        val compileClasspath = configurations.getByName(sourceSet.compileClasspathConfigurationName!!)
        val compileClasspathAttributes = compileClasspath.getAttributes()
        val libraryElements = compileJava.flatMap<kotlin.Boolean?>(Transformer { x: JavaCompile? -> x!!.modularity.getInferModulePath() })
            .map<String?>(Transformer { inferModulePath: kotlin.Boolean? ->
                if (javaClasspathPackaging) {
                    return@map LibraryElements.JAR
                }
                // If we are compiling a module, we require JARs of all dependencies as they may potentially include an Automatic-Module-Name
                val sourcesRoots: MutableList<File?> = CompilationSourceDirs.inferSourceRoots((sourceSet.java!!.getAsFileTree() as org.gradle.api.internal.file.FileTreeInternal?)!!)
                if (JavaModuleDetector.isModuleSource(inferModulePath, sourcesRoots)) {
                    return@map LibraryElements.JAR
                } else {
                    return@map LibraryElements.CLASSES
                }
            })
            .map<LibraryElements?>(Transformer { value: String? -> compileClasspathAttributes.named<LibraryElements?>(LibraryElements::class.java, value) })

        compileClasspathAttributes.attributeProvider<LibraryElements?>(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            libraryElements
        )
    }

    private fun configureTargetPlatform(compileTask: TaskProvider<JavaCompile?>?, sourceSet: SourceSet, configurations: ConfigurationContainer) {
        this.jvmLanguageUtils.useDefaultTargetPlatformInference<JavaCompile?>(configurations.getByName(sourceSet.compileClasspathConfigurationName!!), compileTask)
        this.jvmLanguageUtils.useDefaultTargetPlatformInference<JavaCompile?>(configurations.getByName(sourceSet.runtimeClasspathConfigurationName!!), compileTask)
    }

    private fun createCompileJavaTask(sourceSet: SourceSet, javaSource: SourceDirectorySet, project: Project): TaskProvider<JavaCompile?> {
        val compileTask = project.getTasks().register<JavaCompile?>(sourceSet.compileJavaTaskName, JavaCompile::class.java, Action { javaCompile: JavaCompile? ->
            val conventionMapping = javaCompile!!.getConventionMapping()
            conventionMapping.map("classpath", sourceSet::getCompileClasspath)

            JvmPluginsHelper.configureAnnotationProcessorPath(sourceSet, javaSource, javaCompile.getOptions(), project)
            javaCompile.setDescription("Compiles " + javaSource + ".")
            javaCompile.setSource(javaSource)

            val toolchainOverrideSpec = project.provider<JavaToolchainSpec?>(Callable { JavaCompileExecutableUtils.getExecutableOverrideToolchainSpec(javaCompile, propertyFactory) })
            javaCompile.javaCompiler.convention(
                getToolchainTool<T?>(
                    project,
                    BiFunction { obj: JavaToolchainService?, spec: JavaToolchainSpec? -> obj!!.compilerFor(spec!!) },
                    toolchainOverrideSpec
                )
            )

            val generatedHeadersDir = "generated/sources/headers/" + javaSource.getName() + "/" + sourceSet.name
            javaCompile.getOptions()!!.headerOutputDirectory.convention(project.getLayout().getBuildDirectory().dir(generatedHeadersDir))

            val javaPluginExtension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
            javaCompile.modularity.getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath())
        })
        JvmPluginsHelper.configureOutputDirectoryForSourceSet(sourceSet, javaSource, project, compileTask, compileTask.map<CompileOptions?>(Transformer { obj: JavaCompile? -> obj!!.getOptions() }))

        return compileTask
    }

    private fun createProcessResourcesTask(sourceSet: SourceSet, resourceSet: SourceDirectorySet, target: Project) {
        val processResources = target.getTasks().register<ProcessResources?>(sourceSet.processResourcesTaskName, ProcessResources::class.java, Action { resourcesTask: ProcessResources? ->
            resourcesTask!!.setDescription("Processes " + resourceSet + ".")
            DslObject(resourcesTask.getRootSpec()).getConventionMapping().map("destinationDir", Callable { sourceSet.output!!.resourcesDir } as Callable<File?>)
            resourcesTask.from(resourceSet)
        })
        val output = Cast.uncheckedCast<DefaultSourceSetOutput?>(sourceSet.output)
        output!!.setResourcesContributor(processResources.map<File?>(Transformer { obj: ProcessResources? -> obj!!.getDestinationDir() }), processResources)
    }

    private fun createClassesTask(sourceSet: SourceSet, target: Project) {
        sourceSet.compiledBy(
            target.getTasks().register(sourceSet.classesTaskName!!, Action { classesTask: Task? ->
                classesTask!!.setGroup(LifecycleBasePlugin.BUILD_GROUP)
                classesTask.setDescription("Assembles " + sourceSet.output + ".")
                classesTask.dependsOn(sourceSet.output!!.dirs!!)
                classesTask.dependsOn(sourceSet.compileJavaTaskName!!)
                classesTask.dependsOn(sourceSet.processResourcesTaskName!!)
            })
        )
    }

    private fun defineConfigurationsForSourceSet(sourceSet: SourceSet, configurations: RoleBasedConfigurationContainerInternal) {
        val implementationConfigurationName = sourceSet.implementationConfigurationName
        val runtimeOnlyConfigurationName = sourceSet.runtimeOnlyConfigurationName
        val compileOnlyConfigurationName = sourceSet.compileOnlyConfigurationName
        val compileClasspathConfigurationName = sourceSet.compileClasspathConfigurationName
        val annotationProcessorConfigurationName = sourceSet.annotationProcessorConfigurationName
        val runtimeClasspathConfigurationName = sourceSet.runtimeClasspathConfigurationName
        val sourceSetName: String? = sourceSet.toString()

        val implementationConfiguration: Configuration = configurations.dependencyScopeLocked(implementationConfigurationName, Action { conf: Configuration? ->
            conf!!.setDescription("Implementation only dependencies for " + sourceSetName + ".")
        })

        val compileOnlyConfiguration: Configuration = configurations.dependencyScopeLocked(compileOnlyConfigurationName, Action { conf: Configuration? ->
            conf!!.setDescription("Compile only dependencies for " + sourceSetName + ".")
        })

        val compileClasspathConfiguration: Configuration? = configurations.resolvableLocked(compileClasspathConfigurationName, Action { conf: Configuration? ->
            conf!!.extendsFrom(compileOnlyConfiguration, implementationConfiguration)
            conf.setDescription("Compile classpath for " + sourceSetName + ".")
            jvmPluginServices.configureAsCompileClasspath(conf)
        })

        @Suppress("deprecation") val annotationProcessorConfiguration = configurations.resolvableDependencyScopeLocked(annotationProcessorConfigurationName, Action { conf: Configuration? ->
            conf!!.setDescription("Annotation processors and their dependencies for " + sourceSetName + ".")
            jvmPluginServices.configureAsRuntimeClasspath(conf)
        })

        val runtimeOnlyConfiguration: Configuration = configurations.dependencyScopeLocked(runtimeOnlyConfigurationName, Action { conf: Configuration? ->
            conf!!.setDescription("Runtime only dependencies for " + sourceSetName + ".")
        })

        val runtimeClasspathConfiguration: Configuration? = configurations.resolvableLocked(runtimeClasspathConfigurationName, Action { conf: Configuration? ->
            conf!!.setDescription("Runtime classpath of " + sourceSetName + ".")
            conf.extendsFrom(runtimeOnlyConfiguration, implementationConfiguration)
            jvmPluginServices.configureAsRuntimeClasspath(conf)
        })

        sourceSet.compileClasspath = compileClasspathConfiguration
        sourceSet.runtimeClasspath = sourceSet.output!!.plus(runtimeClasspathConfiguration!!)
        sourceSet.annotationProcessorPath = annotationProcessorConfiguration
    }

    private fun configureCompileDefaults(project: Project, javaExtension: DefaultJavaPluginExtension?) {
        project.getTasks().withType<AbstractCompile?>(AbstractCompile::class.java).configureEach(Action { compile: AbstractCompile? ->
            JvmPluginsHelper.configureCompileDefaults(compile, javaExtension, BiFunction { rawConvention: JavaVersion?, javaVersionSupplier: Supplier<JavaVersion?>? ->
                if (compile is JavaCompile) {
                    val javaCompile = compile
                    if (javaCompile.getOptions()!!.release.isPresent()) {
                        return@configureCompileDefaults JavaVersion.toVersion(javaCompile.getOptions()!!.release.get())
                    }
                    if (rawConvention != null) {
                        return@configureCompileDefaults rawConvention
                    }
                    return@configureCompileDefaults JavaVersion.toVersion(javaCompile.javaCompiler.get().getMetadata().getLanguageVersion().toString())
                }
                javaVersionSupplier!!.get()
            })
        })
    }

    private fun configureJavaDoc(project: Project, javaPluginExtension: JavaPluginExtension) {
        project.getTasks().withType<Javadoc?>(Javadoc::class.java).configureEach(Action { javadoc: Javadoc? ->
            javadoc!!.getConventionMapping().map("destinationDir", Callable { javaPluginExtension.getDocsDir().dir("javadoc").get().getAsFile() })
            javadoc.getConventionMapping().map("title", Callable { ReportUtilities.getApiDocTitleFor(project) })

            val toolchainOverrideSpec = project.provider<JavaToolchainSpec?>(Callable { JavadocExecutableUtils.getExecutableOverrideToolchainSpec(javadoc, propertyFactory) })
            javadoc.getJavadocTool()
                .convention(getToolchainTool<JavadocTool?>(project, BiFunction { obj: JavaToolchainService?, spec: JavaToolchainSpec? -> obj!!.javadocToolFor(spec!!) }, toolchainOverrideSpec))
        })
    }

    private fun configureBuildNeeded(project: Project) {
        project.getTasks().register(BUILD_NEEDED_TASK_NAME, Action { buildTask: Task? ->
            buildTask!!.setDescription("Assembles and tests this project and all projects it depends on.")
            buildTask.setGroup(BasePlugin.BUILD_GROUP)
            buildTask.dependsOn(BUILD_TASK_NAME)
            buildTask.doFirst(SerializableLambdas.action<Task?>(SerializableLambdas.SerializableAction { t: Task? ->
                DeprecationLogger.deprecateTask(BUILD_NEEDED_TASK_NAME)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "deprecate_build_needed_build_dependents_tasks")!!
                    .nagUser()
            }))
        })
    }

    private fun configureBuildDependents(project: Project) {
        project.getTasks().register(BUILD_DEPENDENTS_TASK_NAME, Action { buildTask: Task? ->
            buildTask!!.setDescription("Assembles and tests this project and all projects that depend on it.")
            buildTask.setGroup(BasePlugin.BUILD_GROUP)
            buildTask.dependsOn(BUILD_TASK_NAME)
            buildTask.doFirst(SerializableLambdas.action<Task?>(SerializableLambdas.SerializableAction { t: Task? ->
                DeprecationLogger.deprecateTask(BUILD_DEPENDENTS_TASK_NAME)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "deprecate_build_needed_build_dependents_tasks")!!
                    .nagUser()
            }))
        })
    }

    private fun configureArchiveDefaults(project: Project) {
        // TODO: Gradle 8.1+: Deprecate `getLibsDirectory` in BasePluginExtension and move it to `JavaPluginExtension`
        val basePluginExtension = project.getExtensions().getByType<BasePluginExtension>(BasePluginExtension::class.java)

        project.getTasks().withType<Jar?>(Jar::class.java).configureEach(Action { task: Jar? -> task!!.getDestinationDirectory().convention(basePluginExtension.getLibsDirectory()) })
    }

    private fun configureTest(project: Project, javaPluginExtension: JavaPluginExtension) {
        project.getTasks().withType<Test?>(Test::class.java).configureEach(Action { test: Test? -> configureTestDefaults(test!!, project, javaPluginExtension) })
    }

    private fun configureTestDefaults(test: Test, project: Project, javaPluginExtension: JavaPluginExtension) {
        val htmlReport = test.getReports().getHtml()
        val xmlReport = test.getReports().getJunitXml()

        xmlReport.getOutputLocation().convention(javaPluginExtension.getTestResultsDir().dir(test.getName()))
        htmlReport.getOutputLocation().convention(javaPluginExtension.getTestReportDir().dir(test.getName()))
        test.getBinaryResultsDirectory().convention(javaPluginExtension.getTestResultsDir().dir(test.getName() + "/binary"))
        test.workingDir(project.getProjectDir())

        val toolchainOverrideSpec = project.provider<JavaToolchainSpec?>(Callable { TestExecutableUtils.getExecutableToolchainSpec(test, propertyFactory) })
        test.javaLauncher
            .convention(getToolchainTool<JavaLauncher?>(project, BiFunction { obj: JavaToolchainService?, spec: JavaToolchainSpec? -> obj!!.launcherFor(spec!!) }, toolchainOverrideSpec))
    }

    private fun configureJavaExecTasks(project: Project) {
        project.getTasks().withType<JavaExec?>(JavaExec::class.java).configureEach(Action { javaExec: JavaExec? ->
            val toolchainOverrideSpec = project.provider<JavaToolchainSpec?>(Callable { JavaExecExecutableUtils.getExecutableOverrideToolchainSpec(javaExec!!, propertyFactory) })
            javaExec!!.javaLauncher.convention(getToolchainTool<T?>(project, BiFunction { obj: JavaToolchainService?, spec: JavaToolchainSpec? -> obj!!.launcherFor(spec!!) }, toolchainOverrideSpec))
        })
    }

    private fun <T> getToolchainTool(
        project: Project,
        toolMapper: BiFunction<JavaToolchainService?, JavaToolchainSpec?, Provider<T?>?>,
        toolchainOverride: Provider<JavaToolchainSpec?>
    ): Provider<T?> {
        val service = project.getExtensions().getByType<JavaToolchainService>(JavaToolchainService::class.java)
        val extension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)
        return toolchainOverride.orElse(extension.getToolchain())
            .flatMap<T?>(Transformer { spec: JavaToolchainSpec? -> toolMapper.apply(service, spec) })
    }

    companion object {
        val CHECK_TASK_NAME: String = LifecycleBasePlugin.CHECK_TASK_NAME

        @JvmField
        val VERIFICATION_GROUP: String = LifecycleBasePlugin.VERIFICATION_GROUP
        val BUILD_TASK_NAME: String = LifecycleBasePlugin.BUILD_TASK_NAME

        @Deprecated("")
        const val BUILD_DEPENDENTS_TASK_NAME: String = "buildDependents"

        @Deprecated("")
        const val BUILD_NEEDED_TASK_NAME: String = "buildNeeded"

        /**
         * Task group name for documentation-related tasks.
         */
        @JvmField
        val DOCUMENTATION_GROUP: String = JvmConstants.DOCUMENTATION_GROUP

        /**
         * Set this property to use JARs build from subprojects, instead of the classes folder from these project, on the compile classpath.
         * The main use case for this is to mitigate performance issues on very large multi-projects building on Windows.
         * Setting this property will cause the 'jar' task of all subprojects in the dependency tree to always run during compilation.
         *
         * @since 5.6
         */
        const val COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY: String = "org.gradle.java.compile-classpath-packaging"

        /**
         * A list of known artifact types which are known to prevent from
         * publication.
         *
         * @since 5.3
         */
        @Suppress("unused")
        val UNPUBLISHABLE_VARIANT_ARTIFACTS: MutableSet<String?> = JavaConfigurationVariantMapping.UNPUBLISHABLE_VARIANT_ARTIFACTS

        private fun addExtensions(project: Project): DefaultJavaPluginExtension {
            val toolchainSpec = project.getObjects().newInstance<DefaultToolchainSpec>(DefaultToolchainSpec::class.java)
            val sourceSets = project.getExtensions().getByName("sourceSets") as SourceSetContainer
            return project.getExtensions()
                .create<JavaPluginExtension?>(JavaPluginExtension::class.java, "java", DefaultJavaPluginExtension::class.java, project, sourceSets, toolchainSpec) as DefaultJavaPluginExtension
        }

        private fun definePathsForSourceSet(sourceSet: SourceSet, project: Project) {
            val outputConventionMapping = (sourceSet.output as IConventionAware).getConventionMapping()
            outputConventionMapping.map("resourcesDir", Callable {
                val classesDirName = "resources/" + sourceSet.name
                project.getLayout().getBuildDirectory().dir(classesDirName).get().getAsFile()
            })

            sourceSet.java!!.srcDir("src/" + sourceSet.name + "/java")
            sourceSet.resources!!.srcDir("src/" + sourceSet.name + "/resources")
        }
    }
}
