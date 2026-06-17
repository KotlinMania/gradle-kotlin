/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.tasks.DefaultGroovySourceDirectorySet
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension
import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.internal.ReportUtilities
import org.gradle.api.tasks.GroovyRuntime
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.GroovydocAccess
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Extends [JavaBasePlugin] to provide support for compiling and documenting Groovy
 * source files.
 *
 * @see [Groovy plugin reference](https://docs.gradle.org/current/userguide/groovy_plugin.html)
 */
abstract class GroovyBasePlugin @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val moduleRegistry: ModuleRegistry,
    private val jvmLanguageUtils: JvmLanguageUtilities
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaBasePlugin::class.java)

        val groovyRuntime = project.getExtensions().create<GroovyRuntime>(GROOVY_RUNTIME_EXTENSION_NAME, GroovyRuntime::class.java, project)

        configureCompileDefaults(project, groovyRuntime)
        configureSourceSetDefaults(project)
        configureGroovydoc(project, groovyRuntime)
    }

    private fun configureCompileDefaults(project: Project, groovyRuntime: GroovyRuntime) {
        project.getTasks().withType<GroovyCompile?>(GroovyCompile::class.java).configureEach(Action { compile: GroovyCompile? ->
            compile!!.getConventionMapping().map(
                "groovyClasspath",
                Callable { groovyRuntime.inferGroovyClasspath(compile.getClasspath()!!) }
            )
            val javaExtension = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java) as DefaultJavaPluginExtension
            JvmPluginsHelper.configureCompileDefaults(compile, javaExtension, BiFunction { rawConvention: JavaVersion?, javaVersionSupplier: Supplier<JavaVersion?>? ->
                if (rawConvention != null) {
                    return@configureCompileDefaults rawConvention
                }
                toVersion(compile.javaLauncher.get().getMetadata().getLanguageVersion().toString())
            })
        })
    }

    private fun configureSourceSetDefaults(project: Project) {
        javaPluginExtension(project).sourceSets.all(Action { sourceSet: SourceSet? ->
            val groovySource = getGroovySourceDirectorySet(sourceSet!!)
            sourceSet.getExtensions().add<GroovySourceDirectorySet?>(GroovySourceDirectorySet::class.java, "groovy", groovySource)
            groovySource.srcDir("src/" + sourceSet.name + "/groovy")

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            val groovySourceFiles: FileCollection = groovySource
            sourceSet.resources!!.getFilter().exclude(
                SerializableLambdas.spec<T?>(SerializableLambdas.SerializableSpec { element: T? -> groovySourceFiles.contains(element.getFile()) })
            )
            sourceSet.allJava!!.source(groovySource)
            sourceSet.allSource!!.source(groovySource)

            val compileTask: TaskProvider<GroovyCompile?> = createGroovyCompileTask(project, sourceSet, groovySource)

            val configurations = project.getConfigurations()
            Companion.configureLibraryElements(sourceSet, configurations)
            configureTargetPlatform(compileTask, sourceSet, configurations)
        })
    }

    private fun getGroovySourceDirectorySet(sourceSet: SourceSet): GroovySourceDirectorySet {
        val displayName = (sourceSet as DefaultSourceSet).displayName
        val groovySourceDirectorySet: GroovySourceDirectorySet =
            objectFactory.newInstance<DefaultGroovySourceDirectorySet>(DefaultGroovySourceDirectorySet::class.java, objectFactory.sourceDirectorySet("groovy", displayName + " Groovy source"))
        groovySourceDirectorySet.getFilter().include("**/*.java", "**/*.groovy")
        return groovySourceDirectorySet
    }

    private fun configureTargetPlatform(compileTask: TaskProvider<GroovyCompile?>?, sourceSet: SourceSet, configurations: ConfigurationContainer) {
        jvmLanguageUtils.useDefaultTargetPlatformInference<GroovyCompile?>(configurations.getByName(sourceSet.compileClasspathConfigurationName!!), compileTask)
        jvmLanguageUtils.useDefaultTargetPlatformInference<GroovyCompile?>(configurations.getByName(sourceSet.runtimeClasspathConfigurationName!!), compileTask)
    }

    private fun createGroovyCompileTask(project: Project, sourceSet: SourceSet, groovySource: GroovySourceDirectorySet): TaskProvider<GroovyCompile?> {
        val compileTask = project.getTasks().register<GroovyCompile?>(sourceSet.getCompileTaskName("groovy"), GroovyCompile::class.java, Action { groovyCompile: GroovyCompile? ->
            JvmPluginsHelper.compileAgainstJavaOutputs(groovyCompile, sourceSet, objectFactory)
            JvmPluginsHelper.configureAnnotationProcessorPath(sourceSet, groovySource, groovyCompile!!.getOptions(), project)
            groovyCompile.setDescription("Compiles the " + groovySource + ".")
            groovyCompile.setSource(groovySource)
            groovyCompile.javaLauncher.convention(getJavaLauncher(project))
            groovyCompile.groovyOptions!!.disabledGlobalASTTransformations.convention(Sets.< E > newHashSet < E ? > ("groovy.grape.GrabAnnotationTransformation"))
        })
        JvmPluginsHelper.configureOutputDirectoryForSourceSet(
            sourceSet,
            groovySource,
            project,
            compileTask,
            compileTask.map<CompileOptions?>(Transformer { obj: GroovyCompile? -> obj!!.getOptions() })
        )

        // TODO: `classes` should be a little more tied to the classesDirs for a SourceSet so every plugin
        // doesn't need to do this.
        project.getTasks().named(sourceSet.classesTaskName!!, Action { task: Task? -> task!!.dependsOn(compileTask) })

        return compileTask
    }

    private fun configureGroovydoc(project: Project, groovyRuntime: GroovyRuntime) {
        project.getTasks().withType<Groovydoc?>(Groovydoc::class.java).configureEach(Action { groovydoc: Groovydoc? ->
            groovydoc!!.getConventionMapping().map("groovyClasspath", Callable {
                val groovyClasspath = groovyRuntime.inferGroovyClasspath(groovydoc.classpath!!)
                // Jansi is required to log errors when generating Groovydoc
                val jansi = project.getObjects().fileCollection().from(moduleRegistry.getModule("jansi").getImplementationClasspath().getAsFiles())
                groovyClasspath.plus(jansi)
            })
            groovydoc.getConventionMapping().map("destinationDir", Callable { javaPluginExtension(project).docsDir.dir("groovydoc").get().getAsFile() })
            groovydoc.getConventionMapping().map("docTitle", Callable { ReportUtilities.getApiDocTitleFor(project) })
            groovydoc.getConventionMapping().map("windowTitle", Callable { ReportUtilities.getApiDocTitleFor(project) })
            groovydoc.access.convention(GroovydocAccess.PROTECTED)
            groovydoc.includeAuthor.convention(false)
            groovydoc.processScripts.convention(true)
            groovydoc.includeMainForScripts.convention(true)
        })
    }

    companion object {
        const val GROOVY_RUNTIME_EXTENSION_NAME: String = "groovyRuntime"

        private fun configureLibraryElements(sourceSet: SourceSet, configurations: ConfigurationContainer) {
            // Explain that Groovy, for compile, also needs the resources (#9872)
            configurations.getByName(sourceSet.compileClasspathConfigurationName!!).attributes(Action { attrs: AttributeContainer? ->
                attrs!!.attribute<LibraryElements?>(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    attrs.named<LibraryElements?>(LibraryElements::class.java, LibraryElements.CLASSES_AND_RESOURCES)
                )
            }
            )
        }

        private fun getJavaLauncher(project: Project): Provider<JavaLauncher?> {
            val extension: JavaPluginExtension = javaPluginExtension(project)
            val service: JavaToolchainService = Companion.extensionOf<JavaToolchainService>(project, JavaToolchainService::class.java)!!
            return service.launcherFor(extension.toolchain)
        }

        private fun javaPluginExtension(project: Project): JavaPluginExtension {
            return Companion.extensionOf<JavaPluginExtension>(project, JavaPluginExtension::class.java)!!
        }

        private fun <T> extensionOf(extensionAware: ExtensionAware, type: Class<T?>): T? {
            return extensionAware.getExtensions().getByType<T?>(type)
        }
    }
}
