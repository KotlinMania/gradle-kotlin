/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.internal.Cast
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.internal.TextUtil
import java.util.concurrent.Callable
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Helpers for Jvm plugins. They are in a separate class so that they don't leak
 * into the public API.
 */
object JvmPluginsHelper {
    /**
     * Configures `compileTask` to compile against `sourceSet`'s compile classpath
     * in addition to the outputs of the java compilation, as specified by [SourceSet.getJava]
     *
     * @param compileTask The task to configure.
     * @param sourceSet The source set whose output contains the java classes to compile against.
     * @param objectFactory An [ObjectFactory].
     */
    @JvmStatic
    fun compileAgainstJavaOutputs(compileTask: AbstractCompile, sourceSet: SourceSet, objectFactory: ObjectFactory) {
        val classpath = objectFactory.fileCollection()
        classpath.from(Callable { sourceSet.compileClasspath.plus(objectFactory.fileCollection().from(sourceSet.java!!.getClassesDirectory())) } as Callable<Any?>)
        compileTask.getConventionMapping().map("classpath", Callable { classpath })
    }

    @JvmStatic
    fun configureAnnotationProcessorPath(sourceSet: SourceSet, sourceDirectorySet: SourceDirectorySet, options: CompileOptions, target: Project) {
        val conventionMapping = DslObject(options).getConventionMapping()
        conventionMapping.map("annotationProcessorPath", sourceSet::getAnnotationProcessorPath)
        val annotationProcessorGeneratedSourcesChildPath = "generated/sources/annotationProcessor/" + sourceDirectorySet.getName() + "/" + sourceSet.name
        options.generatedSourceOutputDirectory.convention(target.getLayout().getBuildDirectory().dir(annotationProcessorGeneratedSourcesChildPath))
    }

    fun configureOutputDirectoryForSourceSet(
        sourceSet: SourceSet,
        sourceDirectorySet: SourceDirectorySet,
        target: Project,
        compileTask: TaskProvider<out AbstractCompile?>,
        options: Provider<CompileOptions?>
    ) {
        val sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.name
        sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath))

        val sourceSetOutput = Cast.cast<DefaultSourceSetOutput?, Any?>(DefaultSourceSetOutput::class.java, sourceSet.output)
        sourceSetOutput!!.getClassesDirs().from(sourceDirectorySet.getDestinationDirectory())
        sourceSetOutput.getClassesDirs().builtBy(compileTask)
        sourceSetOutput.getGeneratedSourcesDirs().from(options.flatMap<Any?>(CompileOptions::getGeneratedSourceOutputDirectory))
        sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory)
    }

    fun configureJavaDocTask(displayName: String?, sourceSet: SourceSet, tasks: TaskContainer, javaPluginExtension: JavaPluginExtension?) {
        val javadocTaskName: String = sourceSet.javadocTaskName!!
        if (!tasks.getNames().contains(javadocTaskName)) {
            tasks.register<Javadoc?>(javadocTaskName, Javadoc::class.java, Action { javadoc: Javadoc? ->
                javadoc!!.setDescription("Generates Javadoc API documentation for the " + displayName + ".")
                javadoc.setGroup(JvmConstants.DOCUMENTATION_GROUP)
                javadoc.setClasspath(sourceSet.output!!.plus(sourceSet.compileClasspath))
                javadoc.setSource(sourceSet.allJava!!)
                if (javaPluginExtension != null) {
                    javadoc.getConventionMapping().map("destinationDir", Callable { javaPluginExtension.getDocsDir().dir(javadocTaskName).get().getAsFile() })
                    javadoc.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath())
                }
            })
        }
    }

    fun createInternalDocumentationVariantWithArtifact(
        variantName: String,
        featureName: String?,
        docsType: String,
        capabilities: MutableSet<Capability?>,
        jarTaskName: String,
        artifactSource: Any,
        project: ProjectInternal
    ): NamedDomainObjectProvider<ConsumableConfiguration> {
        val jar: TaskProvider<Jar?> = maybeRegisterDocumentationJarTask(featureName, docsType, jarTaskName, artifactSource, project.getTasks())
        return project.getConfigurations().consumable(variantName, Action { variant: ConsumableConfiguration? ->
            variant!!.setDescription(docsType + " elements for " + (if (featureName == null) "main" else featureName) + ".")
            val attributes = variant.getAttributes()
            attributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attributes.named<Usage?>(Usage::class.java, Usage.JAVA_RUNTIME))
            attributes.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, Category.DOCUMENTATION))
            attributes.attribute<Bundling?>(Bundling.BUNDLING_ATTRIBUTE, attributes.named<Bundling?>(Bundling::class.java, Bundling.EXTERNAL))
            attributes.attribute<DocsType?>(DocsType.DOCS_TYPE_ATTRIBUTE, attributes.named<DocsType?>(DocsType::class.java, docsType))
            capabilities.forEach(Consumer { notation: Capability? -> variant.getOutgoing().capability(notation!!) })
            variant.getOutgoing().artifact(LazyPublishArtifact(jar, project.getFileResolver(), project.getTaskDependencyFactory()))
        })
    }

    /**
     * This was kept for backwards compatibility with https://github.com/vanniktech/gradle-maven-publish-plugin
     */
    fun createDocumentationVariantWithArtifact(
        variantName: String,
        featureName: String?,
        docsType: String,
        capabilities: MutableSet<Capability?>,
        jarTaskName: String,
        artifactSource: Any,
        project: ProjectInternal
    ): Configuration {
        return createInternalDocumentationVariantWithArtifact(variantName, featureName, docsType, capabilities, jarTaskName, artifactSource, project).get()
    }

    private fun maybeRegisterDocumentationJarTask(
        featureName: String?,
        docsType: String?,
        jarTaskName: String,
        artifactSource: Any,
        tasks: TaskContainer
    ): TaskProvider<Jar?> {
        // TODO: Emit deprecation if this task already exists.
        if (tasks.getNames().contains(jarTaskName)) {
            return tasks.named<Jar?>(jarTaskName, Jar::class.java)
        }

        val jarTask = tasks.register<Jar?>(jarTaskName, Jar::class.java, Action { jar: Jar? ->
            jar!!.setDescription("Assembles a jar archive containing the " + (if (featureName == null) "main " + docsType + "." else (docsType + " of the '" + featureName + "' feature.")))
            jar.setGroup(BasePlugin.BUILD_GROUP)
            jar.from(artifactSource)
            jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(if (featureName == null) docsType else (featureName + "-" + docsType)))
        })

        if (tasks.getNames().contains(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)) {
            tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(Action { task: Task? -> task!!.dependsOn(jarTask) })
        }

        return jarTask
    }

    /**
     * Configures the compile task to use the proper source and target compatibility conventions.
     *
     * @param compile The compile task to configure
     * @param javaExtension The java extension containing the raw source and target compatibility values
     * @param compatibilityComputer A function to compute the compatibility version to use as the convention
     * given the raw version values and the current version property values set on the extension
     */
    fun configureCompileDefaults(compile: AbstractCompile, javaExtension: DefaultJavaPluginExtension, compatibilityComputer: BiFunction<JavaVersion?, Supplier<JavaVersion?>?, JavaVersion?>) {
        val conventionMapping = compile.getConventionMapping()
        conventionMapping.map("sourceCompatibility", Callable { computeSourceCompatibilityConvention(javaExtension, compatibilityComputer).toString() })
        conventionMapping.map("targetCompatibility", Callable { computeTargetCompatibilityConvention(javaExtension, compile, compatibilityComputer).toString() })
    }

    private fun computeSourceCompatibilityConvention(javaExtension: DefaultJavaPluginExtension, compatibilityComputer: BiFunction<JavaVersion?, Supplier<JavaVersion?>?, JavaVersion?>): JavaVersion? {
        return compatibilityComputer.apply(javaExtension.getRawSourceCompatibility(), Supplier { javaExtension.getSourceCompatibility() })
    }

    private fun computeTargetCompatibilityConvention(
        javaExtension: DefaultJavaPluginExtension,
        compile: AbstractCompile,
        compatibilityComputer: BiFunction<JavaVersion?, Supplier<JavaVersion?>?, JavaVersion?>
    ): JavaVersion? {
        var rawTargetCompatibility = javaExtension.getRawTargetCompatibility()
        if (rawTargetCompatibility == null) {
            rawTargetCompatibility = toVersion(compile.sourceCompatibility)
        }
        return compatibilityComputer.apply(rawTargetCompatibility, Supplier { javaExtension.getTargetCompatibility() })
    }
}
