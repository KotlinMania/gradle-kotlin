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
package org.gradle.plugins.ide.eclipse

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.PropertiesTransformer
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.JavaTestFixturesPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ide.api.PropertiesFileContentMerger
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper.afterEvaluateOrExecute
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.plugins.ide.eclipse.internal.EclipseProjectMetadata
import org.gradle.plugins.ide.eclipse.internal.LinkedResourcesCreator
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.EclipseJdt
import org.gradle.plugins.ide.eclipse.model.EclipseJdt.getSourceCompatibility
import org.gradle.plugins.ide.eclipse.model.EclipseJdt.getTargetCompatibility
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.Link
import org.gradle.plugins.ide.eclipse.model.internal.EclipseJavaVersionMapper.toEclipseJavaVersion
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.plugins.ide.internal.IdePluginHelper.withGracefulDegradation
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider
import org.gradle.testing.base.TestSuite
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.base.plugins.TestSuiteBasePlugin
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 *
 * A plugin which generates Eclipse files.
 *
 * @see [Eclipse plugin reference](https://docs.gradle.org/current/userguide/eclipse_plugin.html)
 */
abstract class EclipsePlugin @Inject constructor(
    private val uniqueProjectNameProvider: UniqueProjectNameProvider,
    private val artifactRegistry: IdeArtifactRegistry,
    private val jvmPluginServices: JvmPluginServices
) : IdePlugin() {
    private val testSourceSetsConvention: MutableSet<SourceSet?> = HashSet<SourceSet?>()
    private val testConfigurationsConvention: MutableSet<Configuration?> = HashSet<Configuration?>()

    override fun shouldDeprecateLifecycleTask(): Boolean {
        return true
    }

    override fun onApply(project: Project) {
        getLifecycleTask().configure(withDescription("Generates all Eclipse files."))
        getLifecycleTask().configure(withGracefulDegradation())
        getCleanTask().configure(withDescription("Cleans all Eclipse files."))

        val model = project.getExtensions().create<EclipseModel>("eclipse", EclipseModel::class.java, project)

        configureEclipseProject(project as ProjectInternal, model)
        configureEclipseJdt(project, model)
        configureEclipseClasspath(project, model)

        applyEclipseWtpPluginOnWebProjects(project)

        configureRootProjectTask(project)
    }

    private fun configureRootProjectTask(project: Project) {
        // The `eclipse` task in the root project should generate Eclipse projects for all Gradle projects
        if (project.getGradle().getParent() == null && project.getParent() == null) {
            getLifecycleTask().configure(object : Action<Task?> {
                override fun execute(task: Task) {
                    task.dependsOn(artifactRegistry.getIdeProjectFiles(EclipseProjectMetadata::class.java)!!)
                }
            })
        }
    }

    @Suppress("deprecation")
    private fun configureEclipseProject(project: ProjectInternal, model: EclipseModel) {
        val projectModel = model.project

        projectModel!!.name = uniqueProjectNameProvider.getUniqueName(project.getProjectIdentity())

        val convention = (projectModel as IConventionAware).getConventionMapping()
        convention.map("comment", object : Callable<String?> {
            override fun call(): String? {
                return project.getDescription()
            }
        })

        val task = project.getTasks().register<GenerateEclipseProject?>(ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject::class.java, model.project)
        task.configure(object : Action<GenerateEclipseProject?> {
            override fun execute(task: GenerateEclipseProject) {
                task.setDescription("Generates the Eclipse project file.")
                task.inputFile = project.file(".project")
                task.outputFile = project.file(".project")
            }
        })
        addWorker(task, ECLIPSE_PROJECT_TASK_NAME)

        project.getPlugins().withType<JavaBasePlugin?>(JavaBasePlugin::class.java, object : Action<JavaBasePlugin?> {
            override fun execute(javaBasePlugin: JavaBasePlugin?) {
                if (!project.getPlugins().hasPlugin(EarPlugin::class.java)) {
                    projectModel.buildCommand("org.eclipse.jdt.core.javabuilder")
                }

                projectModel.natures("org.eclipse.jdt.core.javanature")
                convention.map("linkedResources", object : Callable<MutableSet<Link?>?> {
                    override fun call(): MutableSet<Link?> {
                        return LinkedResourcesCreator().links(project)
                    }
                })
            }
        })

        project.getPlugins().withType<GroovyBasePlugin?>(GroovyBasePlugin::class.java, object : Action<GroovyBasePlugin?> {
            override fun execute(groovyBasePlugin: GroovyBasePlugin?) {
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }
        })

        project.getPlugins().withType<ScalaBasePlugin?>(ScalaBasePlugin::class.java, object : Action<ScalaBasePlugin?> {
            override fun execute(scalaBasePlugin: ScalaBasePlugin?) {
                projectModel.buildCommands.set(Iterables.indexOf<BuildCommand?>(projectModel.buildCommands, object : Predicate<BuildCommand?> {
                    override fun apply(buildCommand: BuildCommand): Boolean {
                        return buildCommand.name == "org.eclipse.jdt.core.javabuilder"
                    }
                }), BuildCommand("org.scala-ide.sdt.core.scalabuilder"))
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.scala-ide.sdt.core.scalanature")
            }
        })

        artifactRegistry.registerIdeProject(EclipseProjectMetadata(model, project.getProjectDir(), task))
    }

    @Suppress("deprecation")
    private fun configureEclipseClasspath(project: Project, model: EclipseModel) {
        val classpath = project.getObjects().newInstance<EclipseClasspath>(EclipseClasspath::class.java, project)
        classpath.baseSourceOutputDir.convention(project.getLayout().getProjectDirectory().dir("bin"))

        model.setClasspath(classpath)

        (model.getClasspath() as IConventionAware).getConventionMapping().map("defaultOutputDir", object : Callable<File?> {
            override fun call(): File {
                return File(project.getProjectDir(), EclipsePluginConstants.DEFAULT_PROJECT_OUTPUT_PATH)
            }
        })
        model.getClasspath().testSourceSets.convention(testSourceSetsConvention)
        model.getClasspath().testConfigurations.convention(testConfigurationsConvention)

        project.getPlugins().withType<JavaBasePlugin?>(JavaBasePlugin::class.java, object : Action<JavaBasePlugin?> {
            override fun execute(javaBasePlugin: JavaBasePlugin?) {
                val task = project.getTasks().register<GenerateEclipseClasspath?>(ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath::class.java, model.getClasspath())
                task.configure(object : Action<GenerateEclipseClasspath?> {
                    override fun execute(task: GenerateEclipseClasspath) {
                        task.setDescription("Generates the Eclipse classpath file.")
                        task.inputFile = project.file(".classpath")
                        task.outputFile = project.file(".classpath")
                    }
                })
                task.configure(withGracefulDegradation())
                addWorker(task, ECLIPSE_CP_TASK_NAME)

                val xmlTransformer = XmlTransformer()
                xmlTransformer.setIndentation("\t")
                model.getClasspath().file = XmlFileContentMerger(xmlTransformer)
                model.getClasspath().sourceSets = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()

                afterEvaluateOrExecute(project, object : Action<Project?> {
                    override fun execute(p: Project?) {
                        // keep the ordering we had in earlier gradle versions
                        val containers: MutableSet<String?> = LinkedHashSet<String?>()
                        containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/" + whileDisabled<String?>(org.gradle.internal.Factory { model.jdt!!.javaRuntimeName }) + "/")
                        containers.addAll(model.getClasspath().containers)
                        model.getClasspath().containers = containers
                    }
                })

                configureScalaDependencies(project, model)
                configureJavaClasspath(project, task, model, testSourceSetsConvention, testConfigurationsConvention)
            }
        })
    }

    private fun configureScalaDependencies(project: Project, model: EclipseModel) {
        project.getPlugins().withType<ScalaBasePlugin?>(ScalaBasePlugin::class.java, object : Action<ScalaBasePlugin?> {
            override fun execute(scalaBasePlugin: ScalaBasePlugin?) {
                model.getClasspath().containers("org.scala-ide.sdt.launching.SCALA_CONTAINER")

                // exclude the dependencies already provided by SCALA_CONTAINER; prevents problems with Eclipse Scala plugin
                project.getGradle().projectsEvaluated(Action { gradle: Gradle? ->
                    val provided: MutableSet<String?> = ImmutableSet.of<String?>("scala-library", "scala-swing", "scala-dbc")
                    val dependencyInProvided = Predicate { dependency: Dependency? -> provided.contains(dependency!!.getName()) }
                    val dependencies: MutableList<Dependency?> = Lists.newArrayList<Dependency?>(
                        Iterables.filter<Dependency?>(
                            Iterables.concat<Dependency?>(
                                Iterables.transform<Configuration?, Iterable<Dependency?>?>(
                                    model.getClasspath().plusConfigurations!!,
                                    object : Function<Configuration?, Iterable<Dependency?>?> {
                                        override fun apply(config: Configuration): Iterable<Dependency?>? {
                                            return@projectsEvaluated config.getAllDependencies()
                                        }
                                    })
                            ), dependencyInProvided
                        )
                    )
                    if (!dependencies.isEmpty()) {
                        val detachedScalaConfiguration = project.getConfigurations().detachedConfiguration(*dependencies.toTypedArray<Dependency?>())
                        jvmPluginServices.configureAsRuntimeClasspath(detachedScalaConfiguration)
                        model.getClasspath().minusConfigurations!!.add(detachedScalaConfiguration)
                    }
                })
            }
        })
    }

    @Suppress("deprecation")
    private fun configureEclipseJdt(project: Project, model: EclipseModel) {
        project.getPlugins().withType<JavaBasePlugin?>(JavaBasePlugin::class.java, object : Action<JavaBasePlugin?> {
            override fun execute(javaBasePlugin: JavaBasePlugin?) {
                model.jdt = project.getObjects().newInstance<EclipseJdt?>(EclipseJdt::class.java, PropertiesFileContentMerger(PropertiesTransformer()))
                val task = project.getTasks().register<GenerateEclipseJdt?>(ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt::class.java, model.jdt)
                task.configure(object : Action<GenerateEclipseJdt?> {
                    override fun execute(task: GenerateEclipseJdt) {
                        //task properties:
                        task.setDescription("Generates the Eclipse JDT settings file.")
                        task.outputFile = project.file(".settings/org.eclipse.jdt.core.prefs")
                        task.inputFile = project.file(".settings/org.eclipse.jdt.core.prefs")
                    }
                })
                addWorker(task, ECLIPSE_JDT_TASK_NAME)

                //model properties:
                val conventionMapping = (model.jdt as IConventionAware).getConventionMapping()
                conventionMapping.map("sourceCompatibility", object : Callable<JavaVersion?> {
                    override fun call(): JavaVersion? {
                        return project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceCompatibility()
                    }
                })
                conventionMapping.map("targetCompatibility", object : Callable<JavaVersion?> {
                    override fun call(): JavaVersion? {
                        return project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getTargetCompatibility()
                    }
                })
                conventionMapping.map("javaRuntimeName", object : Callable<String?> {
                    override fun call(): String {
                        return eclipseJavaRuntimeNameFor(project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getTargetCompatibility())
                    }
                })
            }
        })
    }

    private fun applyEclipseWtpPluginOnWebProjects(project: Project) {
        val action: Action<Plugin<Project?>?> = createActionApplyingEclipseWtpPlugin()
        project.getPlugins().withType<WarPlugin?>(WarPlugin::class.java, action)
        project.getPlugins().withType<EarPlugin?>(EarPlugin::class.java, action)
    }

    private fun createActionApplyingEclipseWtpPlugin(): Action<Plugin<Project?>?> {
        return object : Action<Plugin<Project?>?> {
            override fun execute(plugin: Plugin<Project?>?) {
                project!!.getPluginManager().apply(EclipseWtpPlugin::class.java)
            }
        }
    }

    companion object {
        val lifecycleTaskName: String = "eclipse"
            get() = Companion.field
        const val ECLIPSE_PROJECT_TASK_NAME: String = "eclipseProject"
        const val ECLIPSE_CP_TASK_NAME: String = "eclipseClasspath"
        const val ECLIPSE_JDT_TASK_NAME: String = "eclipseJdt"

        @Suppress("deprecation")
        private fun configureJavaClasspath(
            project: Project,
            task: TaskProvider<GenerateEclipseClasspath?>,
            model: EclipseModel,
            testSourceSetsConvention: MutableCollection<SourceSet?>,
            testConfigurationsConvention: MutableCollection<Configuration?>
        ) {
            project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, object : Action<JavaPlugin?> {
                override fun execute(javaPlugin: JavaPlugin?) {
                    (model.getClasspath() as IConventionAware).getConventionMapping().map("plusConfigurations", object : Callable<MutableCollection<Configuration?>?> {
                        override fun call(): MutableCollection<Configuration?> {
                            val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
                            val sourceSetsConfigurations: MutableList<Configuration?> = ArrayList<Configuration?>(sourceSets.size * 2)
                            val configurations = project.getConfigurations()
                            for (sourceSet in sourceSets) {
                                sourceSetsConfigurations.add(configurations.getByName(sourceSet.compileClasspathConfigurationName))
                                sourceSetsConfigurations.add(configurations.getByName(sourceSet.runtimeClasspathConfigurationName))
                            }
                            return sourceSetsConfigurations
                        }
                    }).cache()

                    (model.getClasspath() as IConventionAware).getConventionMapping().map("classFolders", object : Callable<MutableList<File?>?> {
                        override fun call(): MutableList<File?> {
                            val result: MutableList<File?> = ArrayList<File?>()
                            for (sourceSet in project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()) {
                                result.addAll(sourceSet.getOutput().getDirs().getFiles())
                            }
                            return result
                        }
                    })

                    task.configure(object : Action<GenerateEclipseClasspath?> {
                        override fun execute(task: GenerateEclipseClasspath) {
                            for (sourceSet in project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()) {
                                task.dependsOn(sourceSet.getOutput().getDirs())
                            }
                        }
                    })

                    val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
                    sourceSets.configureEach(object : Action<SourceSet?> {
                        override fun execute(sourceSet: SourceSet) {
                            if (sourceSet.name.lowercase().contains("test")) {
                                // source sets with 'test' in their name are marked as test on the Eclipse classpath
                                testSourceSetsConvention.add(sourceSet)

                                // resolved dependencies from the source sets with 'test' in their name are marked as test on the Eclipse classpath
                                testConfigurationsConvention.add(project.getConfigurations().findByName(sourceSet.compileClasspathConfigurationName))
                                testConfigurationsConvention.add(project.getConfigurations().findByName(sourceSet.runtimeClasspathConfigurationName))
                            }
                        }
                    })

                    project.getConfigurations().all(object : Action<Configuration?> {
                        override fun execute(configuration: Configuration) {
                            if (configuration.isCanBeResolved() && configuration.getName().lowercase().contains("test")) {
                                // resolved dependencies from custom configurations with 'test' in their name are marked as test on the Eclipse classpath
                                testConfigurationsConvention.add(configuration)
                            }
                        }
                    })
                }
            })

            project.getPlugins().withType<JavaTestFixturesPlugin?>(JavaTestFixturesPlugin::class.java, object : Action<JavaTestFixturesPlugin?> {
                override fun execute(javaTestFixturesPlugin: JavaTestFixturesPlugin?) {
                    model.getClasspath().containsTestFixtures.convention(true)

                    project.getPluginManager().withPlugin("java", object : Action<AppliedPlugin?> {
                        override fun execute(appliedPlugin: AppliedPlugin?) {
                            val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
                            val sourceSet = sourceSets.getByName(TestFixturesSupport.TEST_FIXTURE_SOURCESET_NAME)
                            // the testFixtures source set is marked as test on the Eclipse classpath
                            testSourceSetsConvention.add(sourceSet)

                            // resolved dependencies from the testFixtures source set are marked as test on the Eclipse classpath
                            testConfigurationsConvention.add(project.getConfigurations().findByName(sourceSet.compileClasspathConfigurationName))
                            testConfigurationsConvention.add(project.getConfigurations().findByName(sourceSet.runtimeClasspathConfigurationName))
                        }
                    })
                }
            })

            project.getPlugins().withType<TestSuiteBasePlugin?>(TestSuiteBasePlugin::class.java, Action { testSuiteBasePlugin: TestSuiteBasePlugin? ->
                val testing = project.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
                val suites: ExtensiblePolymorphicDomainObjectContainer<TestSuite?> = testing.getSuites()
                suites.withType<JvmTestSuite?>(JvmTestSuite::class.java).configureEach(Action { jvmTestSuite: JvmTestSuite? ->
                    // jvm test suite source sets are marked as test on the Eclipse classpath
                    testSourceSetsConvention.add(jvmTestSuite!!.sources)

                    // resolved dependencies from jvm test suites are marked as test on the Eclipse classpath
                    testConfigurationsConvention.add(project.getConfigurations().findByName(jvmTestSuite.sources.compileClasspathConfigurationName))
                    testConfigurationsConvention.add(project.getConfigurations().findByName(jvmTestSuite.sources.runtimeClasspathConfigurationName))
                })
            })
        }

        private fun eclipseJavaRuntimeNameFor(version: JavaVersion): String {
            // Default Eclipse JRE paths:
            // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.launching/plugin.xml#L241-L303
            val eclipseJavaVersion = toEclipseJavaVersion(version)
            when (version) {
                JavaVersion.VERSION_1_1 -> return "JRE-1.1"
                JavaVersion.VERSION_1_2, JavaVersion.VERSION_1_3, JavaVersion.VERSION_1_4, JavaVersion.VERSION_1_5 -> return "J2SE-" + eclipseJavaVersion
                else -> return "JavaSE-" + eclipseJavaVersion
            }
        }
    }
}
