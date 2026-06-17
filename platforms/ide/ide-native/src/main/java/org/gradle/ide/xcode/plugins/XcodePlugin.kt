/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.ide.xcode.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer
import org.gradle.ide.xcode.XcodeExtension
import org.gradle.ide.xcode.XcodeRootExtension
import org.gradle.ide.xcode.internal.DefaultXcodeExtension
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.ide.xcode.internal.DefaultXcodeRootExtension
import org.gradle.ide.xcode.internal.DefaultXcodeWorkspace
import org.gradle.ide.xcode.internal.XcodeBinary
import org.gradle.ide.xcode.internal.XcodeProjectMetadata
import org.gradle.ide.xcode.internal.XcodePropertyAdapter
import org.gradle.ide.xcode.internal.XcodeTarget
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget
import org.gradle.ide.xcode.tasks.GenerateSchemeFileTask
import org.gradle.ide.xcode.tasks.GenerateWorkspaceSettingsFileTask
import org.gradle.ide.xcode.tasks.GenerateXcodeProjectFileTask
import org.gradle.ide.xcode.tasks.GenerateXcodeWorkspaceFileTask
import org.gradle.internal.Actions
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.CppSharedLibrary
import org.gradle.language.cpp.CppStaticLibrary
import org.gradle.language.cpp.ProductionCppComponent
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.cpp.plugins.CppApplicationPlugin
import org.gradle.language.cpp.plugins.CppLibraryPlugin
import org.gradle.language.swift.ProductionSwiftComponent
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftExecutable
import org.gradle.language.swift.SwiftSharedLibrary
import org.gradle.language.swift.SwiftStaticLibrary
import org.gradle.language.swift.internal.DefaultSwiftBinary
import org.gradle.language.swift.plugins.SwiftApplicationPlugin
import org.gradle.language.swift.plugins.SwiftLibraryPlugin
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite
import org.gradle.nativeplatform.test.xctest.plugins.XCTestConventionPlugin
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.util.internal.CollectionUtils
import java.io.File
import javax.inject.Inject

/**
 * A plugin for creating a XCode project for a gradle project.
 *
 * @since 4.2
 */
abstract class XcodePlugin @Inject constructor(private val gidGenerator: GidGenerator, private val objectFactory: ObjectFactory, private val artifactRegistry: IdeArtifactRegistry) : IdePlugin() {
    private var xcodeProject: DefaultXcodeProject? = null

    val lifecycleTaskName: String?
        get() = "xcode"

    override fun onApply(project: Project) {
        val lifecycleTask = getLifecycleTask()
        lifecycleTask.configure(withDescription("Generates XCode project files (pbxproj, xcworkspace, xcscheme)"))

        if (isRoot) {
            val xcode = project.getExtensions().create<XcodeRootExtension?>(XcodeRootExtension::class.java, "xcode", DefaultXcodeRootExtension::class.java, objectFactory) as DefaultXcodeRootExtension
            xcodeProject = xcode.getProject()
            val workspaceTask = createWorkspaceTask(project, xcode.getWorkspace())
            lifecycleTask.configure(dependsOn(workspaceTask))
            addWorkspace(xcode.getWorkspace())
        } else {
            val xcode = project.getExtensions().create<XcodeExtension?>(XcodeExtension::class.java, "xcode", DefaultXcodeExtension::class.java, objectFactory) as DefaultXcodeExtension
            xcodeProject = xcode.getProject()
        }

        xcodeProject!!.setLocationDir(project.file(project.getName() + ".xcodeproj"))

        val projectTask = createProjectTask(project as ProjectInternal)
        lifecycleTask.configure(dependsOn(projectTask))

        project.getTasks().addRule("Xcode bridge tasks begin with _xcode. Do not call these directly.", XcodePlugin.XcodeBridge(xcodeProject!!, project))

        configureForSwiftPlugin(project)
        configureForCppPlugin(project)

        includeBuildFilesInProject(project)
        configureXcodeCleanTask(project)
    }

    private fun includeBuildFilesInProject(project: Project) {
        // TODO: Add other build like files `build.gradle.kts`, `settings.gradle(.kts)`, other `.gradle`, `gradle.properties`
        if (project.getBuildFile().exists()) {
            xcodeProject!!.getGroups().getRoot().from(project.getBuildFile())
        }
    }

    private fun configureXcodeCleanTask(project: Project) {
        @Suppress("deprecation") val cleanTask = project.getTasks().create<Delete>("cleanXcodeProject", Delete::class.java)
        cleanTask.delete(xcodeProject!!.getLocationDir())
        if (isRoot) {
            cleanTask.delete(project.file(project.getName() + ".xcworkspace"))
        }
        getCleanTask().configure(Actions.composite(withDescription("Cleans XCode project files (xcodeproj)"), dependsOn(cleanTask)))
    }

    private fun createProjectTask(project: ProjectInternal): GenerateXcodeProjectFileTask {
        val xcodeProjectPackageDir = xcodeProject!!.getLocationDir()

        @Suppress("deprecation") val workspaceSettingsFileTask =
            project.getTasks().create<GenerateWorkspaceSettingsFileTask>("xcodeProjectWorkspaceSettings", GenerateWorkspaceSettingsFileTask::class.java)
        workspaceSettingsFileTask.outputFile = File(xcodeProjectPackageDir, "project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings")

        @Suppress("deprecation") val projectFileTask = project.getTasks().create<GenerateXcodeProjectFileTask>("xcodeProject", GenerateXcodeProjectFileTask::class.java)
        projectFileTask.dependsOn(workspaceSettingsFileTask)
        projectFileTask.dependsOn(xcodeProject!!.getTaskDependencies())
        projectFileTask.dependsOn(project.getTasks().withType<GenerateSchemeFileTask?>(GenerateSchemeFileTask::class.java))
        projectFileTask.setXcodeProject(xcodeProject)
        projectFileTask.outputFile = File(xcodeProjectPackageDir, "project.pbxproj")

        artifactRegistry.registerIdeProject(XcodeProjectMetadata(xcodeProject, projectFileTask))

        return projectFileTask
    }

    private fun createWorkspaceTask(project: Project, workspace: DefaultXcodeWorkspace): GenerateXcodeWorkspaceFileTask {
        val xcodeWorkspacePackageDir = project.file(project.getName() + ".xcworkspace")
        workspace.getLocation().set(xcodeWorkspacePackageDir)

        @Suppress("deprecation") val workspaceSettingsFileTask =
            project.getTasks().create<GenerateWorkspaceSettingsFileTask>("xcodeWorkspaceWorkspaceSettings", GenerateWorkspaceSettingsFileTask::class.java)
        workspaceSettingsFileTask.outputFile = File(xcodeWorkspacePackageDir, "xcshareddata/WorkspaceSettings.xcsettings")

        @Suppress("deprecation") val workspaceFileTask = project.getTasks().create<GenerateXcodeWorkspaceFileTask>("xcodeWorkspace", GenerateXcodeWorkspaceFileTask::class.java)
        workspaceFileTask.dependsOn(workspaceSettingsFileTask)
        workspaceFileTask.outputFile = File(xcodeWorkspacePackageDir, "contents.xcworkspacedata")
        workspaceFileTask.setXcodeProjectLocations(artifactRegistry.getIdeProjectFiles(XcodeProjectMetadata::class.java))

        return workspaceFileTask
    }

    private fun getBridgeTaskPath(project: Project): String {
        var projectPath = ""
        if (!isRoot) {
            projectPath = project.getPath()
        }
        return projectPath + ":_xcode__\${ACTION}_\${PRODUCT_NAME}_\${CONFIGURATION}"
    }

    private fun configureForSwiftPlugin(project: Project) {
        project.getPlugins().withType<SwiftApplicationPlugin?>(SwiftApplicationPlugin::class.java, object : Action<SwiftApplicationPlugin?> {
            override fun execute(plugin: SwiftApplicationPlugin?) {
                configureXcodeForSwift(project)
            }
        })

        project.getPlugins().withType<SwiftLibraryPlugin?>(SwiftLibraryPlugin::class.java, object : Action<SwiftLibraryPlugin?> {
            override fun execute(plugin: SwiftLibraryPlugin?) {
                configureXcodeForSwift(project)
            }
        })

        project.getPlugins().withType<XCTestConventionPlugin?>(XCTestConventionPlugin::class.java, object : Action<XCTestConventionPlugin?> {
            override fun execute(plugin: XCTestConventionPlugin?) {
                configureXcodeForXCTest(project)
            }
        })
    }

    private fun configureXcodeForXCTest(project: Project) {
        project.afterEvaluate(object : Action<Project?> {
            override fun execute(project: Project) {
                val component = project.getExtensions().getByType<SwiftXCTestSuite>(SwiftXCTestSuite::class.java)
                val sources = component.swiftSource
                xcodeProject!!.getGroups().getTests().from(sources)

                val targetName = component.module.get()
                val target = newTarget(targetName, component.module.get(), toGradleCommand(project), getBridgeTaskPath(project), sources)
                target.getSwiftSourceCompatibility().convention(component.sourceCompatibility)
                if (component.getTestBinary().isPresent()) {
                    target.addBinary(
                        DefaultXcodeProject.Companion.BUILD_DEBUG,
                        component.getTestBinary().get().getInstallDirectory(),
                        component.getTestBinary().get().targetMachine.getArchitecture().getName()
                    )
                    target.addBinary(
                        DefaultXcodeProject.Companion.BUILD_RELEASE,
                        component.getTestBinary().get().getInstallDirectory(),
                        component.getTestBinary().get().targetMachine.getArchitecture().getName()
                    )
                    target.setProductType(PBXTarget.ProductType.UNIT_TEST)
                    target.getCompileModules().from(component.getTestBinary().get().compileModules)
                    target.addTaskDependency(filterArtifactsFromImplicitBuilds((component.getTestBinary().get() as DefaultSwiftBinary).importPathConfiguration).getBuildDependencies())
                }
                component.getBinaries().whenElementFinalized(object : Action<SwiftBinary?> {
                    override fun execute(swiftBinary: SwiftBinary) {
                        target.getSwiftSourceCompatibility().set(swiftBinary.targetPlatform.sourceCompatibility)
                    }
                })
                xcodeProject!!.addTarget(target)
            }
        })
    }

    private fun filterArtifactsFromImplicitBuilds(configuration: Configuration): FileCollection {
        return configuration.getIncoming().artifactView(fromSourceDependency()).getArtifacts().getArtifactFiles()
    }

    private fun configureXcodeForSwift(project: Project) {
        project.afterEvaluate(object : Action<Project?> {
            override fun execute(project: Project) {
                // TODO: Assumes there's a single 'main' Swift component
                val component = project.getComponents().withType<ProductionSwiftComponent>(ProductionSwiftComponent::class.java).getByName("main")

                val sources = component.swiftSource
                xcodeProject!!.getGroups().getSources().from(sources)

                // TODO - should use the _install_ task for an executable
                val targetName = component.module.get()
                val target = newTarget(targetName, component.module.get(), toGradleCommand(project), getBridgeTaskPath(project), sources)
                target.getDefaultConfigurationName().set(component.getDevelopmentBinary().map<String?>({ devBinary: SwiftBinary? -> toBuildConfigurationName(component, devBinary!!) }))
                component.getBinaries().whenElementFinalized(object : Action<SwiftBinary?> {
                    override fun execute(swiftBinary: SwiftBinary) {
                        if (swiftBinary is SwiftExecutable) {
                            target.addBinary(toBuildConfigurationName(component, swiftBinary), swiftBinary.debuggerExecutableFile, swiftBinary.targetMachine.getArchitecture().getName())
                            target.setProductType(PBXTarget.ProductType.TOOL)
                        } else if (swiftBinary is SwiftSharedLibrary) {
                            target.addBinary(toBuildConfigurationName(component, swiftBinary), swiftBinary.runtimeFile, swiftBinary.targetMachine.getArchitecture().getName())
                            target.setProductType(PBXTarget.ProductType.DYNAMIC_LIBRARY)
                        } else if (swiftBinary is SwiftStaticLibrary) {
                            target.addBinary(toBuildConfigurationName(component, swiftBinary), swiftBinary.linkFile, swiftBinary.targetMachine.getArchitecture().getName())
                            target.setProductType(PBXTarget.ProductType.STATIC_LIBRARY)
                        }
                        target.getSwiftSourceCompatibility().set(swiftBinary.targetPlatform.sourceCompatibility)

                        if (swiftBinary === component.getDevelopmentBinary().get()) {
                            target.getCompileModules().from(component.getDevelopmentBinary().get().compileModules)
                            target.addTaskDependency(
                                filterArtifactsFromImplicitBuilds(
                                    (component.getDevelopmentBinary().get() as DefaultSwiftBinary).importPathConfiguration
                                ).getBuildDependencies()
                            )

                            Companion.createSchemeTask(project.getTasks(), targetName, xcodeProject!!)
                        }
                    }
                })
                xcodeProject!!.addTarget(target)
            }
        })
    }

    private fun toBuildConfigurationName(component: SoftwareComponent, binary: SoftwareComponent): String {
        val result = binary.getName().replace(component.getName(), "")
        if (binary is SwiftSharedLibrary || binary is CppSharedLibrary) {
            return result.replace("Shared", "")
        } else if (binary is SwiftStaticLibrary || binary is CppStaticLibrary) {
            return result.replace("Static", "")
        }
        return result
    }

    private fun configureForCppPlugin(project: Project) {
        project.getPlugins().withType<CppApplicationPlugin?>(CppApplicationPlugin::class.java, object : Action<CppApplicationPlugin?> {
            override fun execute(plugin: CppApplicationPlugin?) {
                configureXcodeForCpp(project)
            }
        })

        project.getPlugins().withType<CppLibraryPlugin?>(CppLibraryPlugin::class.java, object : Action<CppLibraryPlugin?> {
            override fun execute(plugin: CppLibraryPlugin?) {
                configureXcodeForCpp(project)
            }
        })
    }

    private fun configureXcodeForCpp(project: Project) {
        project.afterEvaluate(object : Action<Project?> {
            override fun execute(project: Project) {
                // TODO: Assumes there's a single 'main' C++ component
                val component = project.getComponents().withType<ProductionCppComponent>(ProductionCppComponent::class.java).getByName("main")

                val sources = component.cppSource
                xcodeProject!!.getGroups().getSources().from(sources)

                val headers: FileCollection = component.headerFiles
                xcodeProject!!.getGroups().getHeaders().from(headers)

                // TODO - should use the _install_ task for an executable
                val targetName = StringUtils.capitalize(component.baseName.get())
                val target = newTarget(targetName, targetName, toGradleCommand(project), getBridgeTaskPath(project), sources)
                target.getDefaultConfigurationName().set(component.getDevelopmentBinary().map<String?>({ devBinary: CppBinary? -> toBuildConfigurationName(component, devBinary!!) }))
                component.getBinaries().whenElementFinalized(object : Action<CppBinary?> {
                    override fun execute(cppBinary: CppBinary?) {
                        if (cppBinary is CppExecutable) {
                            target.addBinary(toBuildConfigurationName(component, cppBinary), cppBinary.debuggerExecutableFile, cppBinary.targetMachine.getArchitecture().getName())
                            target.setProductType(PBXTarget.ProductType.TOOL)
                        } else if (cppBinary is CppSharedLibrary) {
                            target.addBinary(toBuildConfigurationName(component, cppBinary), cppBinary.runtimeFile, cppBinary.targetMachine.getArchitecture().getName())
                            target.setProductType(PBXTarget.ProductType.DYNAMIC_LIBRARY)
                        } else if (cppBinary is CppStaticLibrary) {
                            target.addBinary(toBuildConfigurationName(component, cppBinary), cppBinary.linkFile, cppBinary.targetMachine.getArchitecture().getName())
                            target.setProductType(PBXTarget.ProductType.STATIC_LIBRARY)
                        }

                        if (cppBinary === component.getDevelopmentBinary().get()) {
                            target.getHeaderSearchPaths().from(component.getDevelopmentBinary().get().compileIncludePath)
                            target.getTaskDependencies()
                                .add(filterArtifactsFromImplicitBuilds((component.getDevelopmentBinary().get() as DefaultCppBinary).includePathConfiguration).getBuildDependencies())

                            Companion.createSchemeTask(project.getTasks(), targetName, xcodeProject!!)
                        }
                    }
                })
                target.getHeaderSearchPaths().from(component.privateHeaderDirs)
                if (component is CppLibrary) {
                    target.getHeaderSearchPaths().from(component.publicHeaderDirs)
                }
                xcodeProject!!.addTarget(target)
            }
        })
    }

    private fun newTarget(name: String, productName: String?, gradleCommand: String?, taskName: String?, sources: FileCollection): XcodeTarget {
        val id = gidGenerator.generateGid("PBXLegacyTarget", name.hashCode())
        val target = objectFactory.newInstance<XcodeTarget>(XcodeTarget::class.java, name, id!!)
        target.setTaskName(taskName)
        target.setGradleCommand(gradleCommand)
        target.setProductName(productName)
        target.getSources().setFrom(sources)

        return target
    }

    private class XcodeBridge(private val xcodeProject: DefaultXcodeProject, private val project: Project) : Action<String?> {
        private val xcodePropertyAdapter: XcodePropertyAdapter

        init {
            this.xcodePropertyAdapter = XcodePropertyAdapter(project)
        }

        override fun execute(taskName: String) {
            if (taskName.startsWith("_xcode")) {
                @Suppress("deprecation") val bridgeTask = project.getTasks().create(taskName)
                val action = xcodePropertyAdapter.getAction()
                if (action == "clean") {
                    bridgeTask.dependsOn("clean")
                } else if ("" == action || "build" == action) {
                    val target = findXcodeTarget()
                    if (target.isUnitTest()) {
                        bridgeTestExecution(bridgeTask, target)
                    } else {
                        bridgeProductBuild(bridgeTask, target)
                    }
                } else {
                    throw GradleException("Unrecognized bridge action from Xcode '" + action + "'")
                }
            }
        }

        fun findXcodeTarget(): XcodeTarget {
            val productName = xcodePropertyAdapter.getProductName()
            val target: XcodeTarget = CollectionUtils.findFirst<XcodeTarget?>(xcodeProject.getTargets(), org.gradle.api.specs.Spec { t: XcodeTarget? -> t!!.getProductName() == productName })
            if (target == null) {
                throw GradleException("Unknown Xcode target '" + productName + "', do you need to re-generate Xcode configuration?")
            }
            return target
        }

        fun bridgeProductBuild(bridgeTask: Task, target: XcodeTarget) {
            // Library or executable
            val configuration = xcodePropertyAdapter.getConfiguration()
            bridgeTask.dependsOn(target.getBinaries().stream().filter { it: XcodeBinary? -> it!!.getBuildConfigurationName() == configuration }.findFirst().get().getOutputFile())
        }

        fun bridgeTestExecution(bridgeTask: Task, target: XcodeTarget) {
            // XCTest executable
            // Sync the binary to the BUILT_PRODUCTS_DIR, otherwise Xcode won't find any tests
            val builtProductsPath = xcodePropertyAdapter.getBuiltProductsDir()
            @Suppress("deprecation") val syncTask = project.getTasks().create<Sync>("syncBundleToXcodeBuiltProductDir", Sync::class.java, object : Action<Sync?> {
                override fun execute(task: Sync) {
                    task.from(target.getDebugOutputFile())
                    task.into(builtProductsPath)
                }
            })
            bridgeTask.dependsOn(syncTask)
        }
    }

    private fun fromSourceDependency(): Action<ArtifactView.ViewConfiguration?> {
        return object : Action<ArtifactView.ViewConfiguration?> {
            override fun execute(viewConfiguration: ArtifactView.ViewConfiguration) {
                viewConfiguration.componentFilter(this.isSourceDependency)
            }
        }
    }

    private val isSourceDependency: Spec<ComponentIdentifier?>
        get() = object : Spec<ComponentIdentifier?> {
            override fun isSatisfiedBy(id: ComponentIdentifier?): Boolean {
                if (id is ProjectComponentIdentifier) {
                    // Include as binary when the target project is not included in the workspace
                    return artifactRegistry.getIdeProject<XcodeProjectMetadata?>(
                        XcodeProjectMetadata::class.java,
                        id
                    ) == null
                }
                return false
            }
        }

    companion object {
        private fun createSchemeTask(tasks: TaskContainer, schemeName: String?, xcodeProject: DefaultXcodeProject): GenerateSchemeFileTask {
            // TODO - capitalise the target name in the task name
            // TODO - don't create a launch target for a library
            val name = "xcodeScheme"
            val schemeFileTask = tasks.maybeCreate<GenerateSchemeFileTask>(name, GenerateSchemeFileTask::class.java)
            schemeFileTask.setXcodeProject(xcodeProject)
            schemeFileTask.outputFile = File(xcodeProject.getLocationDir(), "xcshareddata/xcschemes/" + schemeName + ".xcscheme")
            return schemeFileTask
        }
    }
}
