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
package org.gradle.language.plugins

import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.component.PublishableComponent
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.language.ComponentWithBinaries
import org.gradle.language.ComponentWithOutputs
import org.gradle.language.ComponentWithTargetMachines
import org.gradle.language.ProductionComponent
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.cpp.CppBinary
import org.gradle.language.nativeplatform.internal.ComponentWithNames
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithSharedLibrary
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.AbstractLinkTask
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.ExtractSymbols
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.tasks.StripSymbols
import org.gradle.nativeplatform.toolchain.NativeToolChain
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A common base plugin for the native plugins.
 *
 *
 * Expects plugins to register the native components in the [Project.getComponents] container, and defines a number of rules that act on these components to configure them.
 *
 *
 *
 *  * Configures the {@value LifecycleBasePlugin#ASSEMBLE_TASK_NAME} task to build the development binary of the main component, if present. Expects the main component to be of type [ProductionComponent] and [ComponentWithBinaries].
 *
 *  * Adds an `"assemble"` task for each binary of the main component.
 *
 *  * Adds tasks to compile and link an executable. Currently requires component implements internal API [ConfigurableComponentWithExecutable].
 *
 *  * Adds tasks to compile and link a shared library. Currently requires component implements internal API [ConfigurableComponentWithSharedLibrary].
 *
 *  * Adds tasks to compile and create a static library. Currently requires component implements internal API [ConfigurableComponentWithStaticLibrary].
 *
 *  * Adds outgoing configuration and artifacts for link file. Currently requires component implements internal API [ConfigurableComponentWithLinkUsage].
 *
 *  * Adds outgoing configuration and artifacts for runtime file. Currently requires component implements internal API [ConfigurableComponentWithRuntimeUsage].
 *
 *  * Maven publications. Currently requires component implements internal API [PublicationAwareComponent].
 *
 *  * Adds [TargetMachineFactory] for configuring [TargetMachine].
 *
 *
 *
 * @since 4.5
 */
@Incubating
abstract class NativeBasePlugin @Inject constructor(private val targetMachineFactory: TargetMachineFactory) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(LifecycleBasePlugin::class.java)

        addTargetMachineFactoryAsExtension(project.getExtensions(), targetMachineFactory)

        val tasks = project.getTasks()
        val buildDirectory = project.getLayout().getBuildDirectory()

        val components = project.getComponents()

        addLifecycleTasks(project, tasks, components)

        // Add tasks to build various kinds of components
        addTasksForComponentWithExecutable(tasks, buildDirectory, components)
        addTasksForComponentWithSharedLibrary(tasks, buildDirectory, components)
        addTasksForComponentWithStaticLibrary(tasks, buildDirectory, components)

        // Add incoming artifact transforms
        val dependencyHandler = project.getDependencies()

        addHeaderZipTransform(dependencyHandler)

        // Add outgoing configurations and publications
        val configurations = (project as ProjectInternal).getConfigurations()

        project.getDependencies().getAttributesSchema().attribute<Linkage?>(CppBinary.Companion.LINKAGE_ATTRIBUTE).getDisambiguationRules().add(LinkageSelectionRule::class.java)

        addOutgoingConfigurationForLinkUsage(components, configurations)
        addOutgoingConfigurationForRuntimeUsage(components, configurations)

        addPublicationsFromVariants(project, components)
    }

    private fun addLifecycleTasks(project: Project, tasks: TaskContainer, components: SoftwareComponentContainer) {
        components.withType<ComponentWithBinaries?>(ComponentWithBinaries::class.java, Action { component: ComponentWithBinaries? ->
            // Register each child of each component
            component!!.getBinaries().whenElementKnown { binary: SoftwareComponent? -> components.add(binary!!) }

            if (component is ProductionComponent) {
                // Add an assemble task for each binary and also wire the development binary in to the `assemble` task
                component.getBinaries().whenElementFinalized<ComponentWithOutputs?>(ComponentWithOutputs::class.java, Action { binary: ComponentWithOutputs? ->
                    // Determine which output to produce at development time.
                    val outputs = binary!!.getOutputs()
                    val names = (binary as ComponentWithNames).getNames()
                    tasks.register(names.getTaskName("assemble"), Action { task: Task? -> task!!.dependsOn(outputs) })
                    if (binary === (component as ProductionComponent).getDevelopmentBinary().get()) {
                        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, Action { task: Task? -> task!!.dependsOn(outputs) })
                    }
                })
            }
            if (component is ComponentWithTargetMachines) {
                val componentWithTargetMachines = component as ComponentWithTargetMachines
                tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, Action { task: Task? ->
                    task!!.dependsOn(Callable {
                        val currentHost = (targetMachineFactory as DefaultTargetMachineFactory).host()
                        val targetsCurrentMachine = componentWithTargetMachines.getTargetMachines().get().stream()
                            .anyMatch { targetMachine: TargetMachine? -> currentHost.operatingSystemFamily == targetMachine!!.operatingSystemFamily }
                        if (!targetsCurrentMachine) {
                            task.getLogger().warn("'" + component.getName() + "' component in project '" + project.getPath() + "' does not target this operating system.")
                        }
                        mutableListOf<Any?>()
                    } as Callable<*>)
                })
            }
        })
    }

    private fun addTasksForComponentWithExecutable(tasks: TaskContainer, buildDirectory: DirectoryProperty, components: SoftwareComponentContainer) {
        components.withType<ConfigurableComponentWithExecutable?>(ConfigurableComponentWithExecutable::class.java, Action { executable: ConfigurableComponentWithExecutable? ->
            val names = executable!!.getNames()
            val toolChain = executable.getToolChain()
            val targetPlatform = executable.getNativePlatform()
            val toolProvider = executable.getPlatformToolProvider()

            // Add a link task
            val link = tasks.register<LinkExecutable?>(names.getTaskName("link"), LinkExecutable::class.java, Action { task: LinkExecutable? ->
                task!!.source(executable.getObjects())
                task.lib(executable.getLinkLibraries())
                task.linkedFile
                    .set(buildDirectory.file(executable.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getExecutableName("exe/" + names.getDirName() + baseName) })))
                task.targetPlatform.set(targetPlatform)
                task.toolChain.set(toolChain)
                task.getDebuggable().set(executable.isDebuggable())
            })

            executable.getLinkTask().set(link)
            executable.getDebuggerExecutableFile().set(link.flatMap<RegularFile?>(Transformer { linkExecutable: LinkExecutable? -> linkExecutable!!.linkedFile }))

            if (executable.isDebuggable() && executable.isOptimized() && toolProvider.requiresDebugBinaryStripping()) {
                val symbolLocation: Provider<RegularFile?> = buildDirectory.file(
                    executable.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getExecutableSymbolFileName("exe/" + names.getDirName() + "stripped/" + baseName) })
                )
                val strippedLocation: Provider<RegularFile?> = buildDirectory.file(
                    executable.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getExecutableName("exe/" + names.getDirName() + "stripped/" + baseName) })
                )

                val stripSymbols: TaskProvider<StripSymbols?> = stripSymbols(link, names, tasks, toolChain, targetPlatform, strippedLocation)
                executable.getExecutableFile().set(stripSymbols.flatMap<RegularFile?>(Transformer { task: StripSymbols? -> task!!.outputFile }))
                val extractSymbols: TaskProvider<ExtractSymbols?> = extractSymbols(link, names, tasks, toolChain, targetPlatform, symbolLocation)
                executable.getOutputs().from(extractSymbols.flatMap<RegularFile?>(Transformer { task: ExtractSymbols? -> task!!.symbolFile }))
                executable.getExecutableFileProducer().set(stripSymbols)
            } else {
                executable.getExecutableFile().set(link.flatMap<RegularFile?>(Transformer { task: LinkExecutable? -> task!!.linkedFile }))
                executable.getExecutableFileProducer().set(link)
            }

            // Add an install task
            // TODO - should probably not add this for all executables?
            // TODO - add stripped symbols to the installation
            val install = tasks.register<InstallExecutable?>(names.getTaskName("install"), InstallExecutable::class.java, Action { task: InstallExecutable? ->
                task!!.targetPlatform.set(targetPlatform)
                task.toolChain.set(toolChain)
                task.installDirectory.set(buildDirectory.dir("install/" + names.getDirName()))
                task.executableFile.set(executable.getExecutableFile())
                task.lib(executable.getRuntimeLibraries())
            })

            executable.getInstallTask().set(install)
            executable.getInstallDirectory().set(install.flatMap<Directory?>(Transformer { task: InstallExecutable? -> task!!.installDirectory }))
            executable.getOutputs().from(executable.getInstallDirectory())
            executable.getDebuggerExecutableFile().set(install.flatMap<RegularFile?>(Transformer { task: InstallExecutable? -> task!!.installedExecutable }))
        })
    }

    private fun addTasksForComponentWithSharedLibrary(tasks: TaskContainer, buildDirectory: DirectoryProperty, components: SoftwareComponentContainer) {
        components.withType<ConfigurableComponentWithSharedLibrary?>(ConfigurableComponentWithSharedLibrary::class.java, Action { library: ConfigurableComponentWithSharedLibrary? ->
            val names = library!!.getNames()
            val targetPlatform = library.getNativePlatform()
            val toolChain = library.getToolChain()
            val toolProvider = library.getPlatformToolProvider()

            // Add a link task
            val link = tasks.register<LinkSharedLibrary?>(names.getTaskName("link"), LinkSharedLibrary::class.java, Action { task: LinkSharedLibrary? ->
                task!!.source(library.getObjects())
                task.lib(library.getLinkLibraries())
                task.linkedFile
                    .set(buildDirectory.file(library.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getSharedLibraryName("lib/" + names.getDirName() + baseName) })))
                // TODO: We should set this for macOS, but this currently breaks XCTest support for Swift
                // when Swift depends on C++ libraries built by Gradle.
                if (!targetPlatform.getOperatingSystem().isMacOsX()) {
                    val installName = task.linkedFile.getLocationOnly().map<String?>(Transformer { linkedFile: RegularFile? -> linkedFile!!.getAsFile().getName() })
                    task.installName.set(installName)
                }
                task.targetPlatform.set(targetPlatform)
                task.toolChain.set(toolChain)
                task.getDebuggable().set(library.isDebuggable())
            })

            var linkFile = link.flatMap<RegularFile?>(Transformer { task: LinkSharedLibrary? -> task!!.linkedFile })
            var runtimeFile = link.flatMap<RegularFile?>(Transformer { task: LinkSharedLibrary? -> task!!.linkedFile })
            var linkFileTask: Provider<out Task?> = link

            if (toolProvider.producesImportLibrary()) {
                link.configure(Action { linkSharedLibrary: LinkSharedLibrary? ->
                    linkSharedLibrary!!.importLibrary.set(
                        buildDirectory.file(
                            library.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getImportLibraryName("lib/" + names.getDirName() + baseName) })
                        )
                    )
                })
                linkFile = link.flatMap<RegularFile?>(Transformer { task: LinkSharedLibrary? -> task!!.importLibrary })
            }

            if (library.isDebuggable() && library.isOptimized() && toolProvider.requiresDebugBinaryStripping()) {
                val symbolLocation: Provider<RegularFile?> = buildDirectory.file(
                    library.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getLibrarySymbolFileName("lib/" + names.getDirName() + "stripped/" + baseName) })
                )
                val strippedLocation: Provider<RegularFile?> = buildDirectory.file(
                    library.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getSharedLibraryName("lib/" + names.getDirName() + "stripped/" + baseName) })
                )

                val stripSymbols: TaskProvider<StripSymbols?> = stripSymbols(link, names, tasks, toolChain, targetPlatform, strippedLocation)
                runtimeFile = stripSymbols.flatMap<RegularFile?>(Transformer { task: StripSymbols? -> task!!.outputFile })
                linkFile = runtimeFile

                val extractSymbols: TaskProvider<ExtractSymbols?> = extractSymbols(link, names, tasks, toolChain, targetPlatform, symbolLocation)
                library.getOutputs().from(extractSymbols.flatMap<RegularFile?>(Transformer { task: ExtractSymbols? -> task!!.symbolFile }))
                linkFileTask = stripSymbols
            }
            library.getLinkTask().set(link)
            library.getLinkFile().set(linkFile)
            library.getLinkFileProducer().set(linkFileTask)
            library.getRuntimeFile().set(runtimeFile)
            library.getOutputs().from(library.getLinkFile())
            library.getOutputs().from(library.getRuntimeFile())
        })
    }

    private fun addTasksForComponentWithStaticLibrary(tasks: TaskContainer, buildDirectory: DirectoryProperty, components: SoftwareComponentContainer) {
        components.withType<ConfigurableComponentWithStaticLibrary?>(ConfigurableComponentWithStaticLibrary::class.java, Action { library: ConfigurableComponentWithStaticLibrary? ->
            val names = library!!.getNames()
            // Add a create task
            val createTask = tasks.register<CreateStaticLibrary?>(names.getTaskName("create"), CreateStaticLibrary::class.java, Action { task: CreateStaticLibrary? ->
                task!!.source(library.getObjects())
                val toolProvider = library.getPlatformToolProvider()
                val linktimeFile: Provider<RegularFile?> = buildDirectory.file(
                    library.getBaseName().map<String?>(Transformer { baseName: String? -> toolProvider.getStaticLibraryName("lib/" + names.getDirName() + baseName) })
                )
                task.outputFile.set(linktimeFile)
                task.targetPlatform.set(library.getNativePlatform())
                task.toolChain.set(library.getToolChain())
            })

            // Wire the task into the library model
            library.getLinkFile().set(createTask.flatMap<RegularFile?>(Transformer { task: CreateStaticLibrary? -> task!!.getBinaryFile() }))
            library.getLinkFileProducer().set(createTask)
            library.getCreateTask().set(createTask)
            library.getOutputs().from(library.getLinkFile())
        })
    }

    private fun addOutgoingConfigurationForLinkUsage(components: SoftwareComponentContainer, configurations: RoleBasedConfigurationContainerInternal) {
        components.withType<ConfigurableComponentWithLinkUsage?>(ConfigurableComponentWithLinkUsage::class.java, Action { component: ConfigurableComponentWithLinkUsage? ->
            val linkElements: Provider<ConsumableConfiguration?> = configurations.consumable(component!!.getNames().withSuffix("linkElements"), Action { conf: ConsumableConfiguration? ->
                conf!!.extendsFrom(component.getImplementationDependencies())
                copyAttributesTo(component.getLinkAttributes(), conf)
                conf.getOutgoing().artifact(component.getLinkFile())
            })
            component.getLinkElements().set(linkElements)
        })
    }

    private fun addOutgoingConfigurationForRuntimeUsage(components: SoftwareComponentContainer, configurations: RoleBasedConfigurationContainerInternal) {
        components.withType<ConfigurableComponentWithRuntimeUsage?>(ConfigurableComponentWithRuntimeUsage::class.java, Action { component: ConfigurableComponentWithRuntimeUsage? ->
            val runtimeElements: Provider<ConsumableConfiguration?> = configurations.consumable(component!!.getNames().withSuffix("runtimeElements"), Action { conf: ConsumableConfiguration? ->
                conf!!.extendsFrom(component.getImplementationDependencies())
                copyAttributesTo(component.getRuntimeAttributes(), conf)
                if (component.hasRuntimeFile()) {
                    conf.getOutgoing().artifact(component.getRuntimeFile())
                }
            })
            component.getRuntimeElements().set(runtimeElements)
        })
    }

    private fun addPublicationsFromVariants(project: Project, components: SoftwareComponentContainer) {
        project.getPluginManager().withPlugin("maven-publish", Action { plugin: AppliedPlugin? ->
            components.withType<PublicationAwareComponent?>(PublicationAwareComponent::class.java, Action { component: PublicationAwareComponent? ->
                project.getExtensions().configure<PublishingExtension?>(
                    PublishingExtension::class.java, Action { publishing: PublishingExtension? ->
                        val mainVariant = component!!.getMainPublication()
                        publishing!!.getPublications().create<MavenPublication?>("main", MavenPublication::class.java, Action { publication: MavenPublication? ->
                            val publicationInternal = publication as MavenPublicationInternal
                            publicationInternal.getPom().getCoordinates().getArtifactId().set(component.getBaseName())
                            publicationInternal.from(mainVariant)
                            publicationInternal.publishWithOriginalFileName()
                        })

                        val variants: MutableSet<out SoftwareComponent?> = mainVariant.getVariants()
                        if (variants is DomainObjectSet<*>) {
                            (variants as DomainObjectSet<out SoftwareComponent?>).all({ child: SoftwareComponent? -> addPublicationFromVariant(child, publishing, project) })
                        } else {
                            for (variant in variants) {
                                addPublicationFromVariant(variant, publishing, project)
                            }
                        }
                    })
            })
        })
    }

    private fun addPublicationFromVariant(child: SoftwareComponent?, publishing: PublishingExtension, project: Project) {
        if (child is PublishableComponent) {
            publishing.getPublications().create<MavenPublication?>(child.getName(), MavenPublication::class.java, Action { publication: MavenPublication? ->
                val publicationInternal = publication as MavenPublicationInternal
                fillInCoordinates(project, publicationInternal, child)
                publicationInternal.from(child)
                publicationInternal.publishWithOriginalFileName()
            })
        }
    }

    private fun fillInCoordinates(project: Project, publication: MavenPublicationInternal, publishableComponent: PublishableComponent) {
        val coordinates = publishableComponent.getCoordinates()
        val pomCoordinates = publication.getPom().getCoordinates()
        pomCoordinates.getGroupId().set(project.provider<String?>(Callable { coordinates.getGroup() }))
        pomCoordinates.getArtifactId().set(project.provider<String?>(Callable { coordinates.getName() }))
        pomCoordinates.getVersion().set(project.provider<String?>(Callable { coordinates.getVersion() }))
    }

    private fun copyAttributesTo(attributes: AttributeContainer, linkElements: Configuration) {
        for (attribute in attributes.keySet()) {
            val value: Any? = attributes.getAttribute(attribute)
            linkElements.getAttributes().attribute<Any?>(uncheckedCast<Attribute<Any?>?>(attribute), value)
        }
    }

    private fun stripSymbols(
        link: TaskProvider<out AbstractLinkTask?>,
        names: Names,
        tasks: TaskContainer,
        toolChain: NativeToolChain?,
        currentPlatform: NativePlatform?,
        strippedLocation: Provider<RegularFile?>
    ): TaskProvider<StripSymbols?> {
        return tasks.register<StripSymbols?>(names.getTaskName("stripSymbols"), StripSymbols::class.java, Action { stripSymbols: StripSymbols? ->
            stripSymbols!!.binaryFile.set(link.flatMap<RegularFile?>({ task: AbstractLinkTask? -> task!!.linkedFile }))
            stripSymbols.outputFile.set(strippedLocation)
            stripSymbols.targetPlatform.set(currentPlatform)
            stripSymbols.toolChain.set(toolChain)
        })
    }

    private fun extractSymbols(
        link: TaskProvider<out AbstractLinkTask?>,
        names: Names,
        tasks: TaskContainer,
        toolChain: NativeToolChain?,
        currentPlatform: NativePlatform?,
        symbolLocation: Provider<RegularFile?>
    ): TaskProvider<ExtractSymbols?> {
        return tasks.register<ExtractSymbols?>(names.getTaskName("extractSymbols"), ExtractSymbols::class.java, Action { extractSymbols: ExtractSymbols? ->
            extractSymbols!!.binaryFile.set(link.flatMap<RegularFile?>({ task: AbstractLinkTask? -> task!!.linkedFile }))
            extractSymbols.symbolFile.set(symbolLocation)
            extractSymbols.targetPlatform.set(currentPlatform)
            extractSymbols.toolChain.set(toolChain)
        })
    }

    private fun addHeaderZipTransform(dependencyHandler: DependencyHandler) {
        dependencyHandler.registerTransform<TransformParameters.None?>(UnzipTransform::class.java, Action { variantTransform: TransformSpec<TransformParameters.None?>? ->
            val from = variantTransform!!.getFrom()
            from.attribute<String?>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.ZIP_TYPE)
            from.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, from.named<Usage?>(Usage::class.java, Usage.C_PLUS_PLUS_API))
            val to = variantTransform.getTo()
            to.attribute<String?>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
            to.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, to.named<Usage?>(Usage::class.java, Usage.C_PLUS_PLUS_API))
        })
    }

    internal class LinkageSelectionRule : AttributeDisambiguationRule<Linkage?> {
        override fun execute(details: MultipleCandidatesDetails<Linkage?>) {
            if (details.getCandidateValues().contains(Linkage.SHARED)) {
                details.closestMatch(Linkage.SHARED)
            }
        }
    }

    companion object {
        private fun addTargetMachineFactoryAsExtension(extensions: ExtensionContainer, targetMachineFactory: TargetMachineFactory) {
            extensions.add<TargetMachineFactory?>(TargetMachineFactory::class.java, "machines", targetMachineFactory)
        }
    }
}
