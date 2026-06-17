/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.plugins.scala

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultScalaSourceDirectorySet
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension
import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.api.plugins.internal.JvmPluginsHelper.configureAnnotationProcessorPath
import org.gradle.api.plugins.internal.JvmPluginsHelper.configureOutputDirectoryForSourceSet
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.internal.ReportUtilities
import org.gradle.api.tasks.ScalaRuntime
import org.gradle.api.tasks.ScalaSourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.tasks.scala.internal.ScalaRuntimeHelper
import org.gradle.internal.logging.util.Log4jBannedVersion
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.scala.tasks.KeepAliveMode
import java.io.File
import java.util.concurrent.Callable
import java.util.function.BiFunction
import javax.inject.Inject

/**
 *
 * A [Plugin] which compiles and tests Scala sources.
 *
 * @see [Scala plugin reference](https://docs.gradle.org/current/userguide/scala_plugin.html)
 */
abstract class ScalaBasePlugin @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val jvmPluginServices: JvmPluginServices,
    private val dependencyFactory: DependencyFactory
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaBasePlugin::class.java)

        val scalaRuntime = project.getExtensions().create<ScalaRuntime>(SCALA_RUNTIME_EXTENSION_NAME, ScalaRuntime::class.java, project)

        val scalaPluginExtension = project.getExtensions().create<ScalaPluginExtension>("scala", ScalaPluginExtension::class.java)
        scalaPluginExtension.getZincVersion().convention(DEFAULT_ZINC_VERSION)

        val toolchainClasspath: Provider<ResolvableConfiguration?> = createToolchainRuntimeClasspath(project, scalaPluginExtension)

        configureConfigurations(project as ProjectInternal, scalaPluginExtension)

        configureCompileDefaults(project, scalaRuntime, javaPluginExtension(project) as DefaultJavaPluginExtension, scalaPluginExtension, toolchainClasspath)
        configureSourceSetDefaults(project, scalaPluginExtension)
        configureScaladoc(project, scalaRuntime, scalaPluginExtension, toolchainClasspath)
    }

    @Suppress("deprecation")
    private fun configureConfigurations(project: ProjectInternal, scalaPluginExtension: ScalaPluginExtension) {
        val dependencyHandler = project.getDependencies()

        project.getConfigurations().resolvableDependencyScopeLocked(SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME, Action { plugins: Configuration? ->
            plugins!!.setTransitive(false)
            jvmPluginServices.configureAsRuntimeClasspath(plugins)
        })

        project.getConfigurations().resolvableDependencyScopeLocked(ZINC_CONFIGURATION_NAME, Action { zinc: Configuration? ->
            zinc!!.setDescription("The Zinc incremental compiler to be used for this Scala project.")
            zinc.getResolutionStrategy().eachDependency(Action { rule: DependencyResolveDetails? ->
                if (rule!!.getRequested().getGroup() == "com.typesafe.zinc" && rule.getRequested().getName() == "zinc") {
                    rule.useTarget("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + DEFAULT_ZINC_VERSION)
                    rule.because("Typesafe Zinc is no longer maintained.")
                }
            })

            zinc.defaultDependencies(Action { dependencies: DependencySet? ->
                dependencies!!.add(dependencyHandler.create("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + scalaPluginExtension.getZincVersion().get()))
                // Add safeguard and clear error if the user changed the scala version when using default zinc
                zinc.getIncoming().afterResolve(Action { resolvableDependencies: ResolvableDependencies? ->
                    resolvableDependencies!!.getResolutionResult().allComponents(Action { component: ResolvedComponentResult? ->
                        if (component!!.getModuleVersion() != null && component.getModuleVersion()!!.getName() == "scala-library") {
                            if (!component.getModuleVersion()!!.getVersion().startsWith(DEFAULT_SCALA_ZINC_VERSION)) {
                                throw InvalidUserCodeException(
                                    "The version of 'scala-library' was changed while using the default Zinc version. " +
                                            "Version " + component.getModuleVersion()!!
                                        .getVersion() + " is not compatible with org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + DEFAULT_ZINC_VERSION
                                )
                            }
                        }
                    })
                })
            })
            zinc.getDependencyConstraints().add(dependencyHandler.getConstraints().create(Log4jBannedVersion.LOG4J2_CORE_COORDINATES, Action { constraint: DependencyConstraint? ->
                constraint!!.version(
                    Action { version: MutableVersionConstraint? ->
                        version!!.require(Log4jBannedVersion.LOG4J2_CORE_REQUIRED_VERSION)
                        version.reject(Log4jBannedVersion.LOG4J2_CORE_VULNERABLE_VERSION_RANGE)
                    })
            }))
        })

        project.getConfigurations().consumable("incrementalScalaAnalysisElements", Action { incrementalAnalysisElements: ConsumableConfiguration? ->
            incrementalAnalysisElements!!.setDescription("Incremental compilation analysis files")
            val attrs = incrementalAnalysisElements.getAttributes()
            setIncrementalAnalysisUsage(attrs)
            setIncrementalAnalysisCategory(attrs)
        })

        val matchingStrategy = dependencyHandler.getAttributesSchema().attribute<Usage?>(Usage.USAGE_ATTRIBUTE)
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules::class.java, Action { actionConfiguration: ActionConfiguration? ->
            actionConfiguration!!.params(objectFactory.named<Usage?>(Usage::class.java, "scala-analysis"))
            actionConfiguration.params(objectFactory.named<Usage?>(Usage::class.java, Usage.JAVA_API))
            actionConfiguration.params(objectFactory.named<Usage?>(Usage::class.java, Usage.JAVA_RUNTIME))
        })
    }

    private fun createToolchainRuntimeClasspath(project: Project, scalaPluginExtension: ScalaPluginExtension): Provider<ResolvableConfiguration?> {
        val scalaToolchain: Provider<DependencyScopeConfiguration?> = project.getConfigurations().dependencyScope("scalaToolchain", Action { conf: DependencyScopeConfiguration? ->
            conf!!.setDescription("Dependencies for the Scala toolchain")
            conf.getDependencies().addLater(createScalaCompilerDependency(scalaPluginExtension))
            conf.getDependencies().addLater(createScalaBridgeDependency(scalaPluginExtension))
            conf.getDependencies().addLater(createScalaCompilerInterfaceDependency(scalaPluginExtension))
            conf.getDependencies().addLater(createScaladocDependency(scalaPluginExtension))
        })

        return project.getConfigurations().resolvable("scalaToolchainRuntimeClasspath", Action { conf: ResolvableConfiguration? ->
            conf!!.setDescription("Runtime classpath for the Scala toolchain")
            conf.extendsFrom(scalaToolchain.get()!!)
            jvmPluginServices.configureAsRuntimeClasspath(conf)
        })
    }

    private fun configureSourceSetDefaults(project: ProjectInternal, scalaPluginExtension: ScalaPluginExtension) {
        javaPluginExtension(project).sourceSets!!.all({ sourceSet ->
            val scalaSource = createScalaSourceDirectorySet(sourceSet!!)
            sourceSet.getExtensions().add(ScalaSourceDirectorySet::class.java, "scala", scalaSource)
            scalaSource.srcDir(project.file("src/" + sourceSet.name + "/scala"))

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            val scalaSourceFiles: FileCollection = scalaSource
            sourceSet.resources!!.getFilter().exclude(
                SerializableLambdas.spec<T?>(SerializableLambdas.SerializableSpec { element: T? -> scalaSourceFiles.contains(element.getFile()) })
            )
            sourceSet.allJava!!.source(scalaSource)
            sourceSet.allSource!!.source(scalaSource)

            project.getConfigurations().getByName(sourceSet.implementationConfigurationName!!).getDependencies().addLater(createScalaDependency(scalaPluginExtension))

            val incrementalAnalysis: FileCollection = Companion.createIncrementalAnalysisConfigurationFor(project.getConfigurations(), sourceSet)
            createScalaCompileTask(project, sourceSet, scalaSource, incrementalAnalysis)
        })
    }

    /**
     * Determines the scala standard library that user code compiles against.
     */
    private fun createScalaDependency(scalaPluginExtension: ScalaPluginExtension): Provider<Dependency?> {
        return scalaPluginExtension.getScalaVersion().map<Dependency?>(Transformer { scalaVersion: String? ->
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return@map dependencyFactory.create("org.scala-lang", "scala3-library_3", scalaVersion)
            } else {
                return@map dependencyFactory.create("org.scala-lang", "scala-library", scalaVersion)
            }
        })
    }

    /**
     * Determines the Scala compiler dependency.
     */
    private fun createScalaCompilerDependency(scalaPluginExtension: ScalaPluginExtension): Provider<Dependency?> {
        return scalaPluginExtension.getScalaVersion().map<Dependency?>(Transformer { scalaVersion: String? ->
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return@map dependencyFactory.create("org.scala-lang", "scala3-compiler_3", scalaVersion)
            } else {
                return@map dependencyFactory.create("org.scala-lang", "scala-compiler", scalaVersion)
            }
        })
    }

    /**
     * Determines Scala bridge dependency. In Scala 3 it is released for each Scala
     * version together with the compiler jars. For Scala 2 we download sources jar and compile
     * it later on.
     *
     * @see org.gradle.api.internal.tasks.scala.ZincScalaCompilerFactory
     */
    private fun createScalaBridgeDependency(scalaPluginExtension: ScalaPluginExtension): Provider<Dependency?> {
        return scalaPluginExtension.getScalaVersion().zip<String?, Dependency?>(scalaPluginExtension.getZincVersion(), BiFunction { scalaVersion: String?, zincVersion: String? ->
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return@zip dependencyFactory.create("org.scala-lang", "scala3-sbt-bridge", scalaVersion)
            } else {
                val scalaMajorMinorVersion = Joiner.on('.').join(Splitter.on('.').splitToList(scalaVersion!!).subList(0, 2))
                val name = "compiler-bridge_" + scalaMajorMinorVersion
                val dependency: ModuleDependency = dependencyFactory.create("org.scala-sbt", name, zincVersion)

                // Use an artifact to remain compatible with Ivy repositories, which
                // don't support variant derivation.
                dependency.artifact(Action { artifact: DependencyArtifact? ->
                    artifact!!.setClassifier("sources")
                    artifact.setType("jar")
                    artifact.setExtension("jar")
                    artifact.setName(name)
                })

                return@zip dependency
            }
        })
    }

    /**
     * Determines Scala compiler interfaces dependency.
     */
    private fun createScalaCompilerInterfaceDependency(scalaPluginExtension: ScalaPluginExtension): Provider<Dependency?> {
        return scalaPluginExtension.getScalaVersion().zip<String?, Dependency?>(scalaPluginExtension.getZincVersion(), BiFunction { scalaVersion: String?, zincVersion: String? ->
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return@zip dependencyFactory.create("org.scala-lang", "scala3-interfaces", scalaVersion)
            } else {
                return@zip dependencyFactory.create("org.scala-sbt", "compiler-interface", zincVersion)
            }
        })
    }

    /**
     * Determines Scaladoc dependency. Note that scaladoc for Scala 2 is packaged along with the compiler.
     */
    private fun createScaladocDependency(scalaPluginExtension: ScalaPluginExtension): Provider<Dependency?> {
        return scalaPluginExtension.getScalaVersion().map<Dependency?>(Transformer { scalaVersion: String? ->
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return@map dependencyFactory.create("org.scala-lang", "scaladoc_3", scalaVersion)
            } else {
                return@map null
            }
        })
    }

    @Suppress("deprecation")
    private fun createScalaSourceDirectorySet(sourceSet: SourceSet): ScalaSourceDirectorySet {
        val displayName = (sourceSet as DefaultSourceSet).displayName + " Scala source"
        val scalaSourceDirectorySet: ScalaSourceDirectorySet =
            objectFactory.newInstance<DefaultScalaSourceDirectorySet>(DefaultScalaSourceDirectorySet::class.java, objectFactory.sourceDirectorySet("scala", displayName))
        scalaSourceDirectorySet.getFilter().include("**/*.java", "**/*.scala")
        return scalaSourceDirectorySet
    }

    private fun createScalaCompileTask(project: Project, sourceSet: SourceSet, scalaSource: ScalaSourceDirectorySet, incrementalAnalysis: FileCollection) {
        val compileTask = project.getTasks().register<ScalaCompile?>(sourceSet.getCompileTaskName("scala"), ScalaCompile::class.java, Action { scalaCompile: ScalaCompile? ->
            JvmPluginsHelper.compileAgainstJavaOutputs(scalaCompile!!, sourceSet, objectFactory)
            configureAnnotationProcessorPath(sourceSet, scalaSource, scalaCompile.getOptions(), project)
            scalaCompile.setDescription("Compiles the " + scalaSource + ".")
            scalaCompile.setSource(scalaSource)
            scalaCompile.getJavaLauncher().convention(getJavaLauncher(project))
            configureIncrementalAnalysis(project, sourceSet, incrementalAnalysis, scalaCompile)
        })
        configureOutputDirectoryForSourceSet(sourceSet, scalaSource, project, compileTask, compileTask.map<CompileOptions?>(Transformer { obj: ScalaCompile? -> obj.getOptions() }))

        project.getTasks().named(sourceSet.classesTaskName!!, Action { task: Task? -> task!!.dependsOn(compileTask) })
    }

    private fun configureIncrementalAnalysis(project: Project, sourceSet: SourceSet, incrementalAnalysis: FileCollection, scalaCompile: ScalaCompile) {
        scalaCompile.getAnalysisMappingFile().set(project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".mapping"))

        // cannot compute at task execution time because we need association with source set
        val incrementalOptions = scalaCompile.getScalaCompileOptions().incrementalOptions
        incrementalOptions.analysisFile.set(
            project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".analysis")
        )

        incrementalOptions.classfileBackupDir.set(
            project.getLayout().getBuildDirectory().file("tmp/scala/classfileBackup/" + scalaCompile.getName() + ".bak")
        )

        val jarTask = project.getTasks().findByName(sourceSet.jarTaskName!!) as Jar?
        if (jarTask != null) {
            incrementalOptions.publishedCode.set(jarTask.getArchiveFile())
        }
        scalaCompile.getAnalysisFiles().from(incrementalAnalysis)
    }

    internal class UsageDisambiguationRules @Inject constructor(incrementalAnalysis: Usage, javaApi: Usage, private val javaRuntime: Usage) : AttributeDisambiguationRule<Usage?> {
        private val expectedUsages: ImmutableSet<Usage?>

        init {
            this.expectedUsages = ImmutableSet.of<Usage?>(incrementalAnalysis, javaApi, javaRuntime)
        }

        override fun execute(details: MultipleCandidatesDetails<Usage?>) {
            if (details.getConsumerValue() == null) {
                if (details.getCandidateValues() == expectedUsages) {
                    details.closestMatch(javaRuntime)
                }
            }
        }
    }

    companion object {
        /**
         * Default Scala Zinc compiler version
         *
         * @since 6.0
         */
        const val DEFAULT_ZINC_VERSION: String = "1.12.0"
        private const val DEFAULT_SCALA_ZINC_VERSION = "2.13"

        @VisibleForTesting
        const val ZINC_CONFIGURATION_NAME: String = "zinc"
        const val SCALA_RUNTIME_EXTENSION_NAME: String = "scalaRuntime"

        /**
         * Configuration for scala compiler plugins.
         *
         * @since 6.4
         */
        const val SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME: String = "scalaCompilerPlugins"

        private fun setIncrementalAnalysisUsage(attrs: AttributeContainer) {
            attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, "incremental-analysis"))
        }

        private fun setIncrementalAnalysisCategory(attrs: AttributeContainer) {
            attrs.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attrs.named<Category?>(Category::class.java, "scala-analysis"))
        }

        private fun createIncrementalAnalysisConfigurationFor(configurations: RoleBasedConfigurationContainerInternal, sourceSet: SourceSet): FileCollection {
            val classpath = configurations.getByName(sourceSet.compileClasspathConfigurationName!!)
            return classpath.getIncoming().artifactView(Action { viewConfiguration: ArtifactView.ViewConfiguration? ->
                viewConfiguration!!.withVariantReselection()
                viewConfiguration.lenient(true)
                viewConfiguration.componentFilter(SerializableLambdas.spec<ComponentIdentifier?>(SerializableLambdas.SerializableSpec { element: ComponentIdentifier? -> element is ProjectComponentIdentifier }))
                val attrs = viewConfiguration.getAttributes()
                setIncrementalAnalysisUsage(attrs)
                setIncrementalAnalysisCategory(attrs)
            }).getFiles()
        }

        private fun configureCompileDefaults(
            project: Project,
            scalaRuntime: ScalaRuntime,
            javaExtension: DefaultJavaPluginExtension,
            scalaPluginExtension: ScalaPluginExtension,
            scalaToolchainRuntimeClasspath: Provider<ResolvableConfiguration?>
        ) {
            project.getTasks().withType<ScalaCompile?>(ScalaCompile::class.java).configureEach(Action { compile: ScalaCompile? ->
                val conventionMapping = compile!!.getConventionMapping()
                conventionMapping.map("scalaClasspath", Callable {
                    getScalaToolchainClasspath(
                        scalaPluginExtension,
                        scalaToolchainRuntimeClasspath,
                        scalaRuntime,
                        compile.classpath
                    )
                })
                conventionMapping.map("zincClasspath", Callable { project.getConfigurations().getAt(ZINC_CONFIGURATION_NAME) })
                conventionMapping.map("scalaCompilerPlugins", Callable { project.getConfigurations().getAt(SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME) } as Callable<FileCollection?>)
                conventionMapping.map("sourceCompatibility", Callable { Companion.computeJavaSourceCompatibilityConvention(javaExtension, compile).toString() })
                conventionMapping.map("targetCompatibility", Callable { Companion.computeJavaTargetCompatibilityConvention(javaExtension, compile).toString() })
                compile.getScalaCompileOptions().keepAliveMode.convention(KeepAliveMode.SESSION)
            })
        }

        private fun computeJavaSourceCompatibilityConvention(javaExtension: DefaultJavaPluginExtension, compileTask: ScalaCompile): JavaVersion? {
            val rawSourceCompatibility = javaExtension.rawSourceCompatibility
            if (rawSourceCompatibility != null) {
                return rawSourceCompatibility
            }
            return toVersion(compileTask.getJavaLauncher().get().getMetadata().getLanguageVersion().toString())
        }

        private fun computeJavaTargetCompatibilityConvention(javaExtension: DefaultJavaPluginExtension, compileTask: ScalaCompile): JavaVersion? {
            val rawTargetCompatibility = javaExtension.rawTargetCompatibility
            if (rawTargetCompatibility != null) {
                return rawTargetCompatibility
            }
            return toVersion(compileTask.sourceCompatibility)
        }

        private fun configureScaladoc(
            project: Project,
            scalaRuntime: ScalaRuntime,
            scalaPluginExtension: ScalaPluginExtension,
            scalaToolchainRuntimeClasspath: Provider<ResolvableConfiguration?>
        ) {
            project.getTasks().withType<ScalaDoc?>(ScalaDoc::class.java).configureEach(Action { scalaDoc: ScalaDoc? ->
                scalaDoc!!.getConventionMapping().map("scalaClasspath", Callable {
                    getScalaToolchainClasspath(
                        scalaPluginExtension,
                        scalaToolchainRuntimeClasspath,
                        scalaRuntime,
                        scalaDoc.getClasspath()
                    )
                })
                scalaDoc.getConventionMapping().map("destinationDir", Callable { javaPluginExtension(project).docsDir.dir("scaladoc").get().getAsFile() } as Callable<File?>)
                scalaDoc.getConventionMapping().map("title", Callable { ReportUtilities.getApiDocTitleFor(project) })
                scalaDoc.getJavaLauncher().convention(getJavaLauncher(project))
            })
        }

        private fun getScalaToolchainClasspath(
            scalaPluginExtension: ScalaPluginExtension,
            scalaToolchainRuntimeClasspath: Provider<ResolvableConfiguration?>,
            scalaRuntime: ScalaRuntime,
            taskClasspath: FileCollection?
        ): FileCollection? {
            if (scalaPluginExtension.getScalaVersion().isPresent()) {
                return scalaToolchainRuntimeClasspath.get()
            } else {
                // TODO: Deprecate this path in 9.x when we de-incubate ScalaPluginExtension#getScalaVersion()
                return scalaRuntime.inferScalaClasspath(taskClasspath!!)
            }
        }

        private fun getJavaLauncher(project: Project): Provider<JavaLauncher?> {
            val extension: JavaPluginExtension = javaPluginExtension(project)
            val service: JavaToolchainService = Companion.extensionOf<JavaToolchainService>(project, JavaToolchainService::class.java)!!
            return service.launcherFor(extension.toolchain!!)
        }

        private fun javaPluginExtension(project: Project): JavaPluginExtension {
            return Companion.extensionOf<JavaPluginExtension>(project, JavaPluginExtension::class.java)!!
        }

        private fun <T> extensionOf(extensionAware: ExtensionAware, type: Class<T?>): T? {
            return extensionAware.getExtensions().getByType<T?>(type)
        }
    }
}
