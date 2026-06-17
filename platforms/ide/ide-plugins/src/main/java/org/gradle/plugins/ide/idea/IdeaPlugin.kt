/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.model.EclipseJdt.getSourceCompatibility
import org.gradle.plugins.ide.eclipse.model.EclipseJdt.getTargetCompatibility
import org.gradle.plugins.ide.idea.internal.IdeaModuleInternal
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata
import org.gradle.plugins.ide.idea.internal.IdeaModuleSupport
import org.gradle.plugins.ide.idea.internal.IdeaProjectInternal
import org.gradle.plugins.ide.idea.internal.IdeaScalaConfigurer
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModuleIml
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.plugins.ide.idea.model.IdeaWorkspace
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.plugins.ide.idea.model.internal.GeneratedIdeaScope
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.plugins.ide.internal.IdePluginHelper.withGracefulDegradation
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider
import org.gradle.testing.base.TestingExtension
import java.io.File
import java.util.Collections
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Adds a GenerateIdeaModule task. When applied to a root project, also adds a GenerateIdeaProject task. For projects that have the Java plugin applied, the tasks receive additional Java-specific
 * configuration.
 *
 * @see [IDEA plugin reference](https://docs.gradle.org/current/userguide/idea_plugin.html)
 */
abstract class IdeaPlugin @Inject constructor(
    private val uniqueProjectNameProvider: UniqueProjectNameProvider,
    private val artifactRegistry: IdeArtifactRegistry,
    private val projectPathRegistry: ProjectStateRegistry
) : IdePlugin() {
    private var ideaModel: IdeaModel? = null
    private var allJavaProjects: MutableList<Project?>? = null
        get() {
            if (field != null) {
                // cache result because it is pretty expensive to compute
                return field
            }
            field = Lists.newArrayList<Project?>(
                Iterables.filter<Project?>(
                    project!!.getRootProject().getAllprojects(),
                    HAS_IDEA_AND_JAVA_PLUGINS
                )
            )
            return field
        }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    val model: IdeaModel
        get() = ideaModel

    val lifecycleTaskName: String?
        get() = "idea"

    override fun shouldDeprecateLifecycleTask(): Boolean {
        return true
    }

    override fun onApply(project: Project) {
        getLifecycleTask().configure(withDescription("Generates IDEA project files (IML, IPR, IWS)"))
        getLifecycleTask().configure(withGracefulDegradation())
        getCleanTask().configure(withDescription("Cleans IDEA project files (IML, IPR)"))

        ideaModel = project.getExtensions().create<IdeaModel>("idea", IdeaModel::class.java)

        configureIdeaWorkspace(project)
        configureIdeaProject(project)
        configureIdeaModule(project as ProjectInternal)
        configureForJavaPlugin(project)
        configureForWarPlugin(project)
        configureForScalaPlugin()
        configureForTestSuitesPlugin(project)
        linkCompositeBuildDependencies(project)
    }

    @Suppress("deprecation")
    private fun configureIdeaWorkspace(project: Project) {
        val workspace = whileDisabled<IdeaWorkspace?>(
            org.gradle.internal.Factory {
                val iw = this.objectFactory.newInstance<IdeaWorkspace>(IdeaWorkspace::class.java)
                ideaModel!!.setWorkspace(iw)
                iw
            }
        )

        if (isRoot) {
            workspace!!.setIws(XmlFileContentMerger(XmlTransformer()))

            val task = project.getTasks().register<GenerateIdeaWorkspace?>(IDEA_WORKSPACE_TASK_NAME, GenerateIdeaWorkspace::class.java, workspace)
            task.configure(object : Action<GenerateIdeaWorkspace?> {
                override fun execute(task: GenerateIdeaWorkspace) {
                    task.setDescription("Generates an IDEA workspace file (IWS)")
                    task.outputFile = File(project.getProjectDir(), project.getName() + ".iws")
                }
            })
            addWorker(task, IDEA_WORKSPACE_TASK_NAME, false)
        }
    }

    @Suppress("deprecation")
    private fun configureIdeaProject(project: Project) {
        if (isRoot) {
            val ipr = XmlFileContentMerger(XmlTransformer())
            // Instantiating an internal subclass is required for Isolated Projects-safe model building
            val ideaProject: IdeaProject = this.objectFactory.newInstance<IdeaProjectInternal>(IdeaProjectInternal::class.java, project, ipr)
            val projectTask = project.getTasks().register<GenerateIdeaProject?>(IDEA_PROJECT_TASK_NAME, GenerateIdeaProject::class.java, ideaProject)
            projectTask.configure(object : Action<GenerateIdeaProject?> {
                override fun execute(projectTask: GenerateIdeaProject) {
                    projectTask.setDescription("Generates IDEA project file (IPR)")
                }
            })
            projectTask.configure(withGracefulDegradation())
            ideaModel!!.setProject(ideaProject)

            ideaProject.setOutputFile(File(project.getProjectDir(), project.getName() + ".ipr"))
            val conventionMapping = (ideaProject as IConventionAware).getConventionMapping()
            conventionMapping.map("jdkName", object : Callable<String?> {
                override fun call(): String {
                    return JavaVersion.current().toString()
                }
            })
            conventionMapping.map("languageLevel", object : Callable<IdeaLanguageLevel?> {
                override fun call(): IdeaLanguageLevel {
                    val maxSourceCompatibility = getMaxJavaModuleCompatibilityVersionFor(SOURCE_COMPATIBILITY)
                    return IdeaLanguageLevel(maxSourceCompatibility)
                }
            })
            conventionMapping.map("targetBytecodeVersion", object : Callable<JavaVersion?> {
                override fun call(): JavaVersion? {
                    return getMaxJavaModuleCompatibilityVersionFor(TARGET_COMPATIBILITY)
                }
            })

            ideaProject.getWildcards().addAll(mutableListOf<String?>("!?*.class", "!?*.scala", "!?*.groovy", "!?*.java"))
            conventionMapping.map("modules", object : Callable<MutableList<IdeaModule?>?> {
                override fun call(): MutableList<IdeaModule?> {
                    return Lists.newArrayList<IdeaModule?>(Iterables.transform<Project?, IdeaModule?>(Sets.filter<Project?>(project.getRootProject().getAllprojects(), object : Predicate<Project?> {
                        override fun apply(p: Project): Boolean {
                            return p.getPlugins().hasPlugin(IdeaPlugin::class.java)
                        }
                    }), object : Function<Project?, IdeaModule?> {
                        override fun apply(p: Project): IdeaModule {
                            return ideaModelFor(p).getModule()
                        }
                    }))
                }
            })

            conventionMapping.map("pathFactory", object : Callable<PathFactory?> {
                override fun call(): PathFactory {
                    return PathFactory().addPathVariable("PROJECT_DIR", projectTask.get()!!.outputFile!!.getParentFile())
                }
            })

            addWorker(projectTask, IDEA_PROJECT_TASK_NAME)

            addWorkspace(ideaProject)
        }
    }

    private fun getMaxJavaModuleCompatibilityVersionFor(toJavaVersion: Function<Project?, JavaVersion?>): JavaVersion? {
        val allJavaProjects = this.allJavaProjects!!
        if (allJavaProjects.isEmpty()) {
            return IdeaModuleSupport.FALLBACK_MODULE_JAVA_COMPATIBILITY_VERSION
        } else {
            return Collections.max<JavaVersion?>(Lists.transform<Project?, JavaVersion?>(allJavaProjects, toJavaVersion))
        }
    }

    @Suppress("deprecation")
    private fun configureIdeaModule(project: ProjectInternal) {
        // Instantiating an internal subclass is required for Isolated Projects-safe model building
        val module: IdeaModule? = whileDisabled<IdeaModuleInternal?>(org.gradle.internal.Factory {
            this.objectFactory.newInstance<IdeaModuleInternal?>(
                IdeaModuleInternal::class.java, project, IdeaModuleIml(
                    XmlTransformer(), project.getProjectDir()
                )
            )
        }
        )

        val task = project.getTasks().register<GenerateIdeaModule?>(IDEA_MODULE_TASK_NAME, GenerateIdeaModule::class.java, module)
        task.configure(object : Action<GenerateIdeaModule?> {
            override fun execute(task: GenerateIdeaModule) {
                task.setDescription("Generates IDEA module files (IML)")
            }
        })
        task.configure(withGracefulDegradation())
        ideaModel!!.setModule(module)

        val defaultModuleName = uniqueProjectNameProvider.getUniqueName(project.getProjectIdentity())
        module!!.setName(defaultModuleName)

        val conventionMapping = (module as IConventionAware).getConventionMapping()
        val sourceDirs: MutableSet<File?> = LinkedHashSet<File?>()
        conventionMapping.map("sourceDirs", object : Callable<MutableSet<File?>?> {
            override fun call(): MutableSet<File?> {
                return sourceDirs
            }
        })
        conventionMapping.map("contentRoot", object : Callable<File?> {
            override fun call(): File {
                return project.getProjectDir()
            }
        })
        val resourceDirs: MutableSet<File?> = LinkedHashSet<File?>()
        conventionMapping.map("resourceDirs", object : Callable<MutableSet<File?>?> {
            @Throws(Exception::class)
            override fun call(): MutableSet<File?> {
                return resourceDirs
            }
        })
        val excludeDirs: MutableSet<File?> = LinkedHashSet<File?>()
        conventionMapping.map("excludeDirs", object : Callable<MutableSet<File?>?> {
            override fun call(): MutableSet<File?> {
                excludeDirs.add(project.file(".gradle"))
                excludeDirs.add(project.getLayout().getBuildDirectory().getAsFile().get())
                return excludeDirs
            }
        })

        conventionMapping.map("pathFactory", object : Callable<PathFactory?> {
            override fun call(): PathFactory {
                val factory = PathFactory()
                factory.addPathVariable("MODULE_DIR", task.get()!!.outputFile!!.getParentFile())
                for (entry in module.getPathVariables().entries) {
                    factory.addPathVariable(entry.key, entry.value)
                }
                return factory
            }
        })

        artifactRegistry.registerIdeProject(IdeaModuleMetadata(module, task))

        addWorker(task, IDEA_MODULE_TASK_NAME)
    }

    private fun configureForJavaPlugin(project: Project) {
        project.getPlugins().withType<JavaPlugin?>(JavaPlugin::class.java, object : Action<JavaPlugin?> {
            override fun execute(javaPlugin: JavaPlugin?) {
                configureIdeaModuleForJava(project)
            }
        })
    }

    private fun configureForWarPlugin(project: Project) {
        project.getPlugins().withType<WarPlugin?>(WarPlugin::class.java, object : Action<WarPlugin?> {
            override fun execute(warPlugin: WarPlugin?) {
                configureIdeaModuleForWar(project)
            }
        })
    }

    private fun configureForTestSuitesPlugin(project: Project) {
        project.getPlugins().withType<JvmTestSuitePlugin?>(JvmTestSuitePlugin::class.java, object : Action<JvmTestSuitePlugin?> {
            override fun execute(testSuitePlugin: JvmTestSuitePlugin?) {
                configureIdeaModuleForTestSuites(project)
            }
        })
    }

    @Suppress("deprecation")
    private fun configureIdeaModuleForJava(project: Project) {
        val mainFeature = JavaPluginHelper.getJavaComponent(project).mainFeature
        val defaultTestSuite = JavaPluginHelper.getDefaultTestSuite(project)

        project.getTasks().withType<GenerateIdeaModule?>(GenerateIdeaModule::class.java).configureEach(Action { ideaModule: GenerateIdeaModule? ->
            // Dependencies
            ideaModule!!.dependsOn(Callable { mainFeature.sourceSet.getOutput().getDirs().plus(defaultTestSuite.sources.output.dirs) }
            )
        })

        // Defaults
        setupScopes(mainFeature, defaultTestSuite)

        // Convention
        val convention = (ideaModel!!.getModule() as IConventionAware).getConventionMapping()
        val sourceDirs: MutableSet<File?> = LinkedHashSet<File?>()
        convention.map("sourceDirs", object : Callable<MutableSet<File?>?> {
            override fun call(): MutableSet<File?> {
                val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
                sourceDirs.addAll(sourceSets.getByName("main").allJava.getSrcDirs())
                return sourceDirs
            }
        })
        val resourceDirs: MutableSet<File?> = LinkedHashSet<File?>()
        convention.map("resourceDirs", object : Callable<MutableSet<File?>?> {
            override fun call(): MutableSet<File?> {
                val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
                resourceDirs.addAll(sourceSets.getByName("main").resources.getSrcDirs())
                return resourceDirs
            }
        })

        val singleEntryLibraries: MutableMap<String?, FileCollection?> = LinkedHashMap<String?, FileCollection?>(2)
        convention.map("singleEntryLibraries", object : Callable<MutableMap<String?, FileCollection?>?> {
            override fun call(): MutableMap<String?, FileCollection?> {
                val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()
                singleEntryLibraries.putIfAbsent("RUNTIME", sourceSets.getByName("main").output.dirs)
                singleEntryLibraries.putIfAbsent("TEST", sourceSets.getByName("test").output.dirs)
                return singleEntryLibraries
            }
        })
        convention.map("targetBytecodeVersion", object : Callable<JavaVersion?> {
            override fun call(): JavaVersion? {
                val moduleTargetBytecodeLevel: JavaVersion = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getTargetCompatibility()
                return if (includeModuleBytecodeLevelOverride(project.getRootProject(), moduleTargetBytecodeLevel)) moduleTargetBytecodeLevel else null
            }
        })
        convention.map("languageLevel", object : Callable<IdeaLanguageLevel?> {
            override fun call(): IdeaLanguageLevel? {
                val moduleLanguageLevel = IdeaLanguageLevel(project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceCompatibility())
                return if (includeModuleLanguageLevelOverride(project.getRootProject(), moduleLanguageLevel)) moduleLanguageLevel else null
            }
        })
    }

    private fun setupScopes(mainFeature: JvmFeatureInternal, defaultTestSuite: JvmTestSuite) {
        val scopes: MutableMap<String?, MutableMap<String?, MutableCollection<Configuration?>>?> = LinkedHashMap<String?, MutableMap<String?, MutableCollection<Configuration?>>?>()
        for (scope in GeneratedIdeaScope.values()) {
            val plusMinus: MutableMap<String?, MutableCollection<Configuration?>?> = LinkedHashMap<String?, MutableCollection<Configuration?>?>()
            plusMinus.put(IdeaDependenciesProvider.Companion.SCOPE_PLUS, ArrayList<Configuration?>())
            plusMinus.put(IdeaDependenciesProvider.Companion.SCOPE_MINUS, ArrayList<Configuration?>())
            scopes.put(scope.name, plusMinus)
        }

        val provided: MutableCollection<Configuration?> = scopes.get(GeneratedIdeaScope.PROVIDED.name)!!.get(IdeaDependenciesProvider.Companion.SCOPE_PLUS)!!
        provided.add(mainFeature.compileClasspathConfiguration)

        val runtime: MutableCollection<Configuration?> = scopes.get(GeneratedIdeaScope.RUNTIME.name)!!.get(IdeaDependenciesProvider.Companion.SCOPE_PLUS)!!
        runtime.add(mainFeature.runtimeClasspathConfiguration)

        val configurations = project!!.getConfigurations()
        val test: MutableCollection<Configuration?> = scopes.get(GeneratedIdeaScope.TEST.name)!!.get(IdeaDependenciesProvider.Companion.SCOPE_PLUS)!!
        test.add(configurations.getByName(defaultTestSuite.sources.compileClasspathConfigurationName))
        test.add(configurations.getByName(defaultTestSuite.sources.runtimeClasspathConfigurationName))

        ideaModel!!.getModule().setScopes(scopes)
    }

    private fun configureIdeaModuleForTestSuites(project: Project) {
        val testing = project.getExtensions().getByType<TestingExtension>(TestingExtension::class.java)
        val ideaModule: IdeaModule = ideaModelFor(project).getModule()
        testing.getSuites().withType<JvmTestSuite?>(JvmTestSuite::class.java).configureEach(Action { suite: JvmTestSuite? ->
            ideaModule.getTestSources().from(suite!!.sources.allJava.getSourceDirectories())
            ideaModule.getTestResources().from(suite.sources.resources.getSourceDirectories())
        })
    }

    @Suppress("deprecation")
    private fun configureIdeaModuleForWar(project: Project) {
        project.getTasks().withType<GenerateIdeaModule?>(GenerateIdeaModule::class.java).configureEach(object : Action<GenerateIdeaModule?> {
            override fun execute(ideaModule: GenerateIdeaModule) {
                val configurations = project.getConfigurations()
                val providedRuntime = configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
                val providedPlus: MutableCollection<Configuration?> = ideaModule.getModule().getScopes().get(GeneratedIdeaScope.PROVIDED.name)
                    .get(org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider.Companion.SCOPE_PLUS)!!
                providedPlus.add(providedRuntime)
                val runtimeMinus: MutableCollection<Configuration?> = ideaModule.getModule().getScopes().get(GeneratedIdeaScope.RUNTIME.name)
                    .get(org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider.Companion.SCOPE_MINUS)!!
                runtimeMinus.add(providedRuntime)
                val testMinus: MutableCollection<Configuration?> = ideaModule.getModule().getScopes().get(GeneratedIdeaScope.TEST.name)
                    .get(org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider.Companion.SCOPE_MINUS)!!
                testMinus.add(providedRuntime)
            }
        })
    }

    private fun configureForScalaPlugin() {
        val isolatedProjects = this.buildFeatures.getIsolatedProjects().getActive().get()
        project!!.getPlugins().withType<ScalaBasePlugin?>(ScalaBasePlugin::class.java, object : Action<ScalaBasePlugin?> {
            override fun execute(scalaBasePlugin: ScalaBasePlugin?) {
                ideaModuleDependsOnRoot(isolatedProjects)
            }
        })
        if (isRoot) {
            IdeaScalaConfigurer(project, Consumer { scalaProjects: MutableCollection<Project?>? ->
                if (!scalaProjects!!.isEmpty() && isolatedProjects) {
                    failOnIncompatibleWithIsolatedProjects()
                }
            }).configure()
        }
    }

    private fun ideaModuleDependsOnRoot(isolatedProjects: Boolean) {
        if (isolatedProjects) {
            failOnIncompatibleWithIsolatedProjects()
        }

        // see IdeaScalaConfigurer which requires the ipr to be generated first
        project!!.getTasks().named(IDEA_MODULE_TASK_NAME, dependsOn(project!!.getRootProject().getTasks().named(IDEA_PROJECT_TASK_NAME)))
    }

    private fun linkCompositeBuildDependencies(project: ProjectInternal) {
        if (isRoot) {
            getLifecycleTask().configure(
                { task: Task? ->
                    task!!.dependsOn(
                        TaskDependencyContainer { context: TaskDependencyResolveContext? -> visitAllImlArtifactsInComposite(project, ideaModel!!.getProject(), context) }
                    )
                }
            )
        }
    }

    @Suppress("deprecation")
    private fun visitAllImlArtifactsInComposite(project: ProjectInternal, ideaProject: IdeaProject, context: TaskDependencyResolveContext?) {
        val thisProjectId = projectPathRegistry.stateFor(project).getComponentIdentifier()
        for (reference in artifactRegistry.getIdeProjects<IdeaModuleMetadata?>(IdeaModuleMetadata::class.java)!!) {
            val otherBuildId = reference!!.owningProject!!.getBuild()
            if (thisProjectId.getBuild() == otherBuildId) {
                // IDEA Module for project in current build: don't include any module that has been excluded from project
                var found = false
                for (ideaModule in ideaProject.getModules()) {
                    if (reference.get()!!.file == ideaModule.getOutputFile()) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    continue
                }
            }
            reference.visitDependencies(context)
        }
    }

    @get:Inject
    protected abstract val buildFeatures: BuildFeatures?

    companion object {
        private val HAS_IDEA_AND_JAVA_PLUGINS: Predicate<Project?> = object : Predicate<Project?> {
            override fun apply(project: Project): Boolean {
                return project.getPlugins().hasPlugin(IdeaPlugin::class.java) && project.getPlugins().hasPlugin(JavaBasePlugin::class.java)
            }
        }
        val SOURCE_COMPATIBILITY: Function<Project?, JavaVersion?> = object : Function<Project?, JavaVersion?> {
            override fun apply(p: Project): JavaVersion? {
                return p.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceCompatibility()
            }
        }
        val TARGET_COMPATIBILITY: Function<Project?, JavaVersion?> = object : Function<Project?, JavaVersion?> {
            override fun apply(p: Project): JavaVersion {
                return p.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getTargetCompatibility()
            }
        }

        private const val IDEA_MODULE_TASK_NAME = "ideaModule"
        private const val IDEA_PROJECT_TASK_NAME = "ideaProject"
        private const val IDEA_WORKSPACE_TASK_NAME = "ideaWorkspace"

        private fun ideaModelFor(project: Project): IdeaModel {
            return project.getExtensions().getByType<IdeaModel>(IdeaModel::class.java)
        }

        private fun includeModuleBytecodeLevelOverride(rootProject: Project, moduleTargetBytecodeLevel: JavaVersion): Boolean {
            if (!rootProject.getPlugins().hasPlugin(IdeaPlugin::class.java)) {
                return true
            }

            val ideaProject: IdeaProject = ideaModelFor(rootProject).getProject()
            return moduleTargetBytecodeLevel != ideaProject.getTargetBytecodeVersion()
        }

        private fun includeModuleLanguageLevelOverride(rootProject: Project, moduleLanguageLevel: IdeaLanguageLevel): Boolean {
            if (!rootProject.getPlugins().hasPlugin(IdeaPlugin::class.java)) {
                return true
            }

            val ideaProject: IdeaProject = ideaModelFor(rootProject).getProject()
            return !moduleLanguageLevel.equals(ideaProject.getLanguageLevel())
        }

        private fun failOnIncompatibleWithIsolatedProjects() {
            throw GradleException("Applying 'idea' plugin to Scala projects is not supported with Isolated Projects. Disable Isolated Projects to use this integration.")
        }
    }
}
