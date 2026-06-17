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
package org.gradle.nativeplatform.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Namer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.Cast
import org.gradle.internal.build.BuildState
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal
import org.gradle.model.Defaults
import org.gradle.model.Each
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.FlavorContainer
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.PrebuiltLibraries
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.Repositories
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.TargetedNativeComponent
import org.gradle.nativeplatform.internal.DefaultBuildTypeContainer
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.internal.DefaultFlavorContainer
import org.gradle.nativeplatform.internal.DefaultNativeExecutableBinarySpec
import org.gradle.nativeplatform.internal.DefaultNativeExecutableSpec
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec
import org.gradle.nativeplatform.internal.DefaultSharedLibraryBinarySpec
import org.gradle.nativeplatform.internal.DefaultStaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.NativeComponents
import org.gradle.nativeplatform.internal.NativeDependentBinariesResolutionStrategy
import org.gradle.nativeplatform.internal.NativeExecutableBinarySpecInternal
import org.gradle.nativeplatform.internal.NativePlatformResolver
import org.gradle.nativeplatform.internal.SharedLibraryBinarySpecInternal
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal
import org.gradle.nativeplatform.internal.configure.NativeComponentRules
import org.gradle.nativeplatform.internal.pch.PchEnabledLanguageTransform
import org.gradle.nativeplatform.internal.prebuilt.DefaultPrebuiltLibraries
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryInitializer
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.tasks.PrefixHeaderFileGenerateTask
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.Platform
import org.gradle.platform.base.PlatformContainer
import org.gradle.platform.base.SourceComponentSpec
import org.gradle.platform.base.TypeBuilder
import org.gradle.platform.base.internal.HasIntermediateOutputsComponentSpec
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver
import java.io.File
import javax.inject.Inject

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
abstract class NativeComponentModelPlugin @Inject @Suppress("unused") constructor(instantiator: Instantiator?, collectionCallbackActionDecorator: CollectionCallbackActionDecorator?) :
    Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(ComponentModelBasePlugin::class.java)
        project.getPluginManager().apply(NativeComponentPlugin::class.java)
        project.getPluginManager().apply(StandardToolChainsPlugin::class.java)
        project.getExtensions().create<BuildTypeContainer?>(BuildTypeContainer::class.java, "buildTypes", DefaultBuildTypeContainer::class.java)
        project.getExtensions().create<FlavorContainer?>(FlavorContainer::class.java, "flavors", DefaultFlavorContainer::class.java)
    }

    internal class Rules : RuleSource() {
        @ComponentType
        fun nativeExecutable(builder: TypeBuilder<NativeExecutableSpec?>) {
            builder.defaultImplementation(DefaultNativeExecutableSpec::class.java)
        }

        @ComponentType
        fun nativeLibrary(builder: TypeBuilder<NativeLibrarySpec?>) {
            builder.defaultImplementation(DefaultNativeLibrarySpec::class.java)
        }

        @ComponentType
        fun registerTargetedNativeComponent(builder: TypeBuilder<TargetedNativeComponent?>) {
            builder.internalView(TargetedNativeComponentInternal::class.java)
        }

        @ComponentType
        fun registerNativeComponent(builder: TypeBuilder<NativeComponentSpec?>) {
            builder.internalView(HasIntermediateOutputsComponentSpec::class.java)
        }

        @Model
        fun repositories(
            serviceRegistry: ServiceRegistry,
            flavors: FlavorContainer?,
            platforms: PlatformContainer,
            buildTypes: BuildTypeContainer?,
            callbackActionDecorator: CollectionCallbackActionDecorator
        ): Repositories {
            val instantiator = serviceRegistry.get<Instantiator?>(Instantiator::class.java)
            val sourceDirectorySetFactory = serviceRegistry.get<ObjectFactory?>(ObjectFactory::class.java)
            val nativePlatforms = serviceRegistry.get<NativePlatforms?>(NativePlatforms::class.java)
            val fileCollectionFactory = serviceRegistry.get<FileCollectionFactory?>(FileCollectionFactory::class.java)
            val initializer: Action<PrebuiltLibrary?> =
                PrebuiltLibraryInitializer(instantiator, fileCollectionFactory, nativePlatforms, platforms.withType<NativePlatform?>(NativePlatform::class.java), buildTypes, flavors)
            val domainObjectCollectionFactory = serviceRegistry.get<DomainObjectCollectionFactory?>(DomainObjectCollectionFactory::class.java)
            return NativeComponentModelPlugin.DefaultRepositories(instantiator!!, sourceDirectorySetFactory, initializer, callbackActionDecorator, domainObjectCollectionFactory)
        }

        @Model
        fun toolChains(extensionContainer: ExtensionContainer): NativeToolChainRegistryInternal? {
            return Cast.cast<NativeToolChainRegistryInternal?, NativeToolChainRegistry?>(
                NativeToolChainRegistryInternal::class.java, extensionContainer.getByType<NativeToolChainRegistry?>(
                    NativeToolChainRegistry::class.java
                )
            )
        }

        @Model
        fun buildTypes(extensionContainer: ExtensionContainer): BuildTypeContainer {
            return extensionContainer.getByType<BuildTypeContainer>(BuildTypeContainer::class.java)
        }

        @Model
        fun flavors(extensionContainer: ExtensionContainer): FlavorContainer {
            return extensionContainer.getByType<FlavorContainer>(FlavorContainer::class.java)
        }

        @Mutate
        fun registerNativePlatformResolver(resolvers: PlatformResolvers, serviceRegistry: ServiceRegistry) {
            resolvers.register(serviceRegistry.get<NativePlatformResolver?>(NativePlatformResolver::class.java))
        }

        @Defaults
        fun registerFactoryForCustomNativePlatforms(platforms: PlatformContainer, instantiator: Instantiator) {
            val nativePlatformFactory: NamedDomainObjectFactory<NativePlatform?> = object : NamedDomainObjectFactory<NativePlatform?> {
                override fun create(name: String): NativePlatform {
                    return instantiator.newInstance<DefaultNativePlatform>(DefaultNativePlatform::class.java, name)
                }
            }

            platforms.registerFactory<NativePlatform?>(NativePlatform::class.java, nativePlatformFactory)

            platforms.registerFactory<Platform?>(Platform::class.java, nativePlatformFactory)
        }

        @ComponentType
        fun registerSharedLibraryBinaryType(builder: TypeBuilder<SharedLibraryBinarySpec?>) {
            builder.defaultImplementation(DefaultSharedLibraryBinarySpec::class.java)
            builder.internalView(SharedLibraryBinarySpecInternal::class.java)
        }

        @ComponentType
        fun registerStaticLibraryBinaryType(builder: TypeBuilder<StaticLibraryBinarySpec?>) {
            builder.defaultImplementation(DefaultStaticLibraryBinarySpec::class.java)
            builder.internalView(StaticLibraryBinarySpecInternal::class.java)
        }

        @ComponentType
        fun registerNativeExecutableBinaryType(builder: TypeBuilder<NativeExecutableBinarySpec?>) {
            builder.defaultImplementation(DefaultNativeExecutableBinarySpec::class.java)
            builder.internalView(NativeExecutableBinarySpecInternal::class.java)
        }

        @Finalize
        fun createDefaultBuildTypes(buildTypes: BuildTypeContainer) {
            if (buildTypes.isEmpty()) {
                buildTypes.create("debug")
            }
        }

        @Finalize
        fun createDefaultFlavor(flavors: FlavorContainer) {
            if (flavors.isEmpty()) {
                flavors.create(DefaultFlavor.Companion.DEFAULT)
            }
        }

        @Finalize
        fun configureGeneratedSourceSets(@Each languageSourceSet: LanguageSourceSetInternal) {
            val generatorTask: Task = languageSourceSet.generatorTask
            if (generatorTask != null) {
                languageSourceSet.builtBy(generatorTask)
                maybeSetSourceDir(languageSourceSet.source, generatorTask, "sourceDir")
                if (languageSourceSet is HeaderExportingSourceSet) {
                    maybeSetSourceDir((languageSourceSet as HeaderExportingSourceSet).exportedHeaders, generatorTask, "headerDir")
                }
            }
        }

        @Defaults
        fun configurePrefixHeaderFiles(@Each componentSpec: SourceComponentSpec, @Path("buildDir") buildDir: File?) {
            componentSpec.getSources().withType<DependentSourceSetInternal?>(DependentSourceSetInternal::class.java).afterEach(object : Action<DependentSourceSetInternal?> {
                override fun execute(dependentSourceSet: DependentSourceSetInternal) {
                    if (dependentSourceSet.preCompiledHeader != null) {
                        val prefixHeaderDirName = "tmp/" + componentSpec.getName() + "/" + dependentSourceSet.getName() + "/prefixHeaders"
                        val prefixHeaderDir = File(buildDir, prefixHeaderDirName)
                        val prefixHeaderFile = File(prefixHeaderDir, "prefix-headers.h")
                        dependentSourceSet.prefixHeaderFile = prefixHeaderFile
                    }
                }
            })
        }

        @Suppress("deprecation")
        @Mutate
        fun configurePrefixHeaderGenerationTasks(tasks: TaskContainer, components: ComponentSpecContainer) {
            for (nativeComponentSpec in components.withType<SourceComponentSpec>(SourceComponentSpec::class.java).values()) {
                for (dependentSourceSet in nativeComponentSpec.getSources().withType<DependentSourceSetInternal>(DependentSourceSetInternal::class.java).values()) {
                    if (dependentSourceSet.prefixHeaderFile != null) {
                        val taskName = "generate" + StringUtils.capitalize(nativeComponentSpec.getName()) + StringUtils.capitalize(dependentSourceSet.getName()) + "PrefixHeaderFile"
                        tasks.create<PrefixHeaderFileGenerateTask?>(taskName, PrefixHeaderFileGenerateTask::class.java, object : Action<PrefixHeaderFileGenerateTask?> {
                            override fun execute(prefixHeaderFileGenerateTask: PrefixHeaderFileGenerateTask) {
                                prefixHeaderFileGenerateTask.setPrefixHeaderFile(dependentSourceSet.prefixHeaderFile)
                                prefixHeaderFileGenerateTask.setHeader(dependentSourceSet.preCompiledHeader)
                            }
                        })
                    }
                }
            }
        }

        @Mutate
        fun configurePreCompiledHeaderCompileTasks(tasks: TaskContainer, binaries: BinaryContainer, languageTransforms: LanguageTransformContainer, serviceRegistry: ServiceRegistry?) {
            for (nativeBinarySpec in binaries.withType<NativeBinarySpecInternal>(NativeBinarySpecInternal::class.java)) {
                for (transform in languageTransforms.withType<PchEnabledLanguageTransform<*>?>(PchEnabledLanguageTransform::class.java)) {
                    nativeBinarySpec.inputs.withType(transform!!.sourceSetType, object : Action<LanguageSourceSet?> {
                        override fun execute(languageSourceSet: LanguageSourceSet?) {
                            val dependentSourceSet = languageSourceSet as DependentSourceSet
                            if (dependentSourceSet.preCompiledHeader != null) {
                                nativeBinarySpec.addPreCompiledHeaderFor(dependentSourceSet)
                                val pchTransformTaskConfig = transform.getPchTransformTask()
                                val pchTaskName =
                                    pchTransformTaskConfig.taskPrefix + StringUtils.capitalize(nativeBinarySpec.projectScopedName) + StringUtils.capitalize(dependentSourceSet.getName()) + "PreCompiledHeader"
                                @Suppress("deprecation") val pchTask: Task = tasks.create(pchTaskName, pchTransformTaskConfig.taskType, object : Action<DefaultTask?> {
                                    override fun execute(task: DefaultTask?) {
                                        pchTransformTaskConfig.configureTask(task, nativeBinarySpec, dependentSourceSet, serviceRegistry)
                                    }
                                })
                                nativeBinarySpec.tasks.add(pchTask)
                            }
                        }
                    })
                }
            }
        }

        private fun maybeSetSourceDir(sourceSet: SourceDirectorySet, task: Task, propertyName: String) {
            val value = task.property(propertyName)
            if (value != null) {
                sourceSet.srcDir(value)
            }
        }

        @BinaryTasks
        fun sharedLibraryTasks(tasks: ModelMap<Task?>, binary: SharedLibraryBinarySpecInternal) {
            val taskName = binary.namingScheme.getTaskName("link")
            tasks.create<LinkSharedLibrary?>(taskName, LinkSharedLibrary::class.java, object : Action<LinkSharedLibrary?> {
                override fun execute(linkTask: LinkSharedLibrary) {
                    linkTask.setDescription("Links " + binary.displayName)
                    linkTask.toolChain!!.set(binary.getToolChain())
                    linkTask.targetPlatform!!.set(binary.getTargetPlatform())
                    linkTask.linkedFile.set(binary.getSharedLibraryFile())
                    linkTask.installName.set(binary.getSharedLibraryFile().getName())
                    linkTask.linkerArgs.set(binary.getLinker().getArgs())
                    linkTask.importLibrary.set(binary.getSharedLibraryLinkFile())

                    linkTask.lib(object : NativeComponents.BinaryLibs(binary) {
                        override fun getFiles(nativeDependencySet: NativeDependencySet): FileCollection? {
                            return nativeDependencySet.getLinkFiles()
                        }
                    })
                }
            })
        }

        @BinaryTasks
        fun staticLibraryTasks(tasks: ModelMap<Task?>, binary: StaticLibraryBinarySpecInternal) {
            val taskName = binary.namingScheme.getTaskName("create")
            tasks.create<CreateStaticLibrary?>(taskName, CreateStaticLibrary::class.java, object : Action<CreateStaticLibrary?> {
                override fun execute(task: CreateStaticLibrary) {
                    task.setDescription("Creates " + binary.displayName)
                    task.toolChain.set(binary.getToolChain())
                    task.targetPlatform.set(binary.getTargetPlatform())
                    task.outputFile.set(binary.getStaticLibraryFile())
                    task.staticLibArgs.set(binary.getStaticLibArchiver().getArgs())
                }
            })
        }

        @BinaryTasks
        fun executableTasks(tasks: ModelMap<Task?>?, executableBinary: NativeExecutableBinarySpecInternal) {
            NativeComponents.createExecutableTask(executableBinary, executableBinary.getExecutable().getFile())
        }

        @Defaults
        fun createBuildDependentComponentsTasks(tasks: ModelMap<Task?>?, components: ComponentSpecContainer, binaries: BinaryContainer?) {
            NativeComponents.createBuildDependentComponentsTasks(tasks, components)
        }

        @BinaryTasks
        fun createBuildDependentBinariesTasks(tasks: ModelMap<Task?>?, nativeBinary: NativeBinarySpecInternal) {
            NativeComponents.createBuildDependentBinariesTasks(nativeBinary, nativeBinary.namingScheme)
        }

        @Finalize
        fun wireBuildDependentTasks(tasks: ModelMap<Task?>?, binaries: BinaryContainer, dependentsResolver: DependentBinariesResolver?, serviceRegistry: ServiceRegistry) {
            NativeComponents.wireBuildDependentTasks(tasks, binaries, dependentsResolver, serviceRegistry.get<ProjectModelResolver?>(ProjectModelResolver::class.java))
        }

        /**
         * Can't use @BinaryTasks because the binary is not _built-by_ the install task, but it is associated with it. Rule is called multiple times, so need to check for task existence before
         * creating.
         */
        @Defaults
        fun createInstallTasks(tasks: ModelMap<Task?>?, binaries: BinaryContainer) {
            for (binary in binaries.withType<NativeExecutableBinarySpecInternal>(NativeExecutableBinarySpecInternal::class.java).values()) {
                NativeComponents.createInstallTask(binary, binary.getInstallation(), binary.getExecutable(), binary.namingScheme)
            }
        }

        @Finalize
        fun applyHeaderSourceSetConventions(@Each headerSourceSet: HeaderExportingSourceSet) {
            // Only apply default locations when none explicitly configured
            if (headerSourceSet.exportedHeaders.getSourceDirectories().isEmpty()) {
                headerSourceSet.exportedHeaders.srcDir("src/" + headerSourceSet.parentName + "/headers")
            }

            headerSourceSet.implicitHeaders.setSrcDirs(headerSourceSet.source.getSourceDirectories())
            headerSourceSet.implicitHeaders.include("**/*.h")
        }

        @Finalize
        fun createBinaries(
            @Each nativeComponent: TargetedNativeComponentInternal?,
            platforms: PlatformResolvers?,
            buildTypes: BuildTypeContainer?,
            flavors: FlavorContainer?,
            serviceRegistry: ServiceRegistry
        ) {
            val nativePlatforms = serviceRegistry.get<NativePlatforms?>(NativePlatforms::class.java)
            val nativeDependencyResolver = serviceRegistry.get<NativeDependencyResolver?>(NativeDependencyResolver::class.java)
            val fileCollectionFactory = serviceRegistry.get<FileCollectionFactory?>(FileCollectionFactory::class.java)
            NativeComponentRules.createBinariesImpl(nativeComponent, platforms, buildTypes, flavors, nativePlatforms, nativeDependencyResolver, fileCollectionFactory)
        }

        @Defaults
        fun registerNativeDependentBinariesResolutionStrategy(resolver: DependentBinariesResolver, serviceRegistry: ServiceRegistry) {
            val projectRegistry = serviceRegistry.get<BuildState?>(BuildState::class.java)!!.getProjects()
            val projectModelResolver = serviceRegistry.get<ProjectModelResolver?>(ProjectModelResolver::class.java)
            resolver.register(NativeDependentBinariesResolutionStrategy(projectRegistry, projectModelResolver))
        }
    }

    private class DefaultRepositories(
        instantiator: Instantiator,
        objectFactory: ObjectFactory?,
        binaryFactory: Action<PrebuiltLibrary?>?,
        collectionCallbackActionDecorator: CollectionCallbackActionDecorator,
        domainObjectCollectionFactory: DomainObjectCollectionFactory?
    ) : DefaultPolymorphicDomainObjectContainer<ArtifactRepository?>(ArtifactRepository::class.java, instantiator, ArtifactRepositoryNamer(), collectionCallbackActionDecorator), Repositories {
        init {
            registerFactory<PrebuiltLibraries?>(PrebuiltLibraries::class.java, object : NamedDomainObjectFactory<PrebuiltLibraries?> {
                override fun create(name: String): PrebuiltLibraries {
                    return instantiator.newInstance<DefaultPrebuiltLibraries>(
                        DefaultPrebuiltLibraries::class.java,
                        name,
                        instantiator,
                        objectFactory,
                        binaryFactory,
                        collectionCallbackActionDecorator,
                        domainObjectCollectionFactory
                    )
                }
            })
        }
    }

    private class ArtifactRepositoryNamer : Namer<ArtifactRepository?> {
        override fun determineName(`object`: ArtifactRepository): String {
            return `object`.getName()
        }
    }
}
