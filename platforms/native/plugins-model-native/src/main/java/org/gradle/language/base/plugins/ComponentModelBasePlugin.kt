/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.base.plugins

import com.google.common.base.Joiner
import com.google.common.base.Strings
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyUtil
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.internal.plugins.PublishingPluginRules
import org.gradle.api.tasks.TaskContainer
import org.gradle.ide.visualstudio.internal.plugins.VisualStudioPluginRules
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.language.base.internal.model.BinarySourceTransformations
import org.gradle.language.base.internal.registry.DefaultLanguageTransformContainer
import org.gradle.language.base.internal.registry.LanguageTransformContainer
import org.gradle.model.Defaults
import org.gradle.model.Each
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleInput
import org.gradle.model.RuleSource
import org.gradle.model.RuleTarget
import org.gradle.model.Rules
import org.gradle.model.internal.core.Hidden
import org.gradle.model.internal.core.NamedEntityInstantiator
import org.gradle.platform.base.ApplicationSpec
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.GeneralComponentSpec
import org.gradle.platform.base.LibrarySpec
import org.gradle.platform.base.PlatformAwareComponentSpec
import org.gradle.platform.base.PlatformContainer
import org.gradle.platform.base.SourceComponentSpec
import org.gradle.platform.base.TypeBuilder
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.DefaultPlatformContainer
import org.gradle.platform.base.internal.DefaultPlatformResolvers
import org.gradle.platform.base.internal.HasIntermediateOutputsComponentSpec
import org.gradle.platform.base.internal.PlatformAwareComponentSpecInternal
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.platform.base.internal.dependents.BaseDependentBinariesResolutionStrategy
import org.gradle.platform.base.internal.dependents.DefaultDependentBinariesResolver
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver
import org.gradle.platform.base.plugins.BinaryBasePlugin
import java.io.File

/**
 * Base plugin for component support.
 *
 * Adds a [ComponentSpecContainer] named `components` to the model.
 *
 * For each binary instance added to the binaries container, registers a lifecycle task to create that binary.
 */
@Incubating
abstract class ComponentModelBasePlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(LanguageBasePlugin::class.java)
        project.getPluginManager().apply(BinaryBasePlugin::class.java)

        project.getPluginManager().withPlugin("org.gradle.publishing", Action { appliedPlugin: AppliedPlugin? ->
            project.getPluginManager().apply(PublishingPluginRules::class.java)
        })

        project.getPluginManager().withPlugin("org.gradle.visual-studio", Action { appliedPlugin: AppliedPlugin? ->
            project.getPluginManager().apply(VisualStudioPluginRules.VisualStudioExtensionRules::class.java)
            if (project === project.getRootProject()) {
                project.getPluginManager().apply(VisualStudioPluginRules.VisualStudioPluginRootRules::class.java)
            }
            project.getPluginManager().apply(VisualStudioPluginRules.VisualStudioPluginProjectRules::class.java)
        })
    }

    internal class PluginRules : RuleSource() {
        @ComponentType
        fun registerGeneralComponentSpec(builder: TypeBuilder<GeneralComponentSpec?>) {
            builder.defaultImplementation(BaseComponentSpec::class.java)
        }

        @ComponentType
        fun registerLibrarySpec(builder: TypeBuilder<LibrarySpec?>) {
            builder.defaultImplementation(BaseComponentSpec::class.java)
        }

        @ComponentType
        fun registerApplicationSpec(builder: TypeBuilder<ApplicationSpec?>) {
            builder.defaultImplementation(BaseComponentSpec::class.java)
        }

        @ComponentType
        fun registerPlatformAwareComponent(builder: TypeBuilder<PlatformAwareComponentSpec?>) {
            builder.internalView(PlatformAwareComponentSpecInternal::class.java)
        }

        @Hidden
        @Model
        fun languageTransforms(collectionCallbackActionDecorator: CollectionCallbackActionDecorator?): LanguageTransformContainer {
            return DefaultLanguageTransformContainer(collectionCallbackActionDecorator)
        }

        // Finalizing here, as we need this to run after any 'assembling' task (jar, link, etc) is created.
        // TODO: Convert this to `@BinaryTasks` when we model a `NativeAssembly` instead of wiring compile tasks directly to LinkTask
        @Finalize
        fun createSourceTransformTasks(
            tasks: TaskContainer?,
            @Path("binaries") binaries: ModelMap<BinarySpecInternal>,
            languageTransforms: LanguageTransformContainer?,
            serviceRegistry: ServiceRegistry?
        ) {
            val transformations = BinarySourceTransformations(tasks!!, languageTransforms!!, serviceRegistry!!)
            for (binary in binaries) {
                if (binary.isLegacyBinary) {
                    continue
                }

                transformations.createTasksFor(binary)
            }
        }

        @Model
        fun projectLayout(projectIdentifier: ProjectIdentifier?, @Path("buildDir") buildDir: File?): ProjectLayout {
            return ProjectLayout(projectIdentifier, buildDir)
        }

        @Model
        fun platforms(instantiator: Instantiator, collectionCallbackActionDecorator: CollectionCallbackActionDecorator?): PlatformContainer {
            return instantiator.newInstance<DefaultPlatformContainer>(DefaultPlatformContainer::class.java, instantiator, collectionCallbackActionDecorator)
        }

        @Hidden
        @Model
        fun platformResolver(platforms: PlatformContainer): PlatformResolvers {
            return DefaultPlatformResolvers(platforms)
        }

        @Mutate
        fun registerPlatformExtension(extensions: ExtensionContainer, platforms: PlatformContainer) {
            extensions.add<PlatformContainer?>(PlatformContainer::class.java, "platforms", platforms)
        }

        @Mutate
        fun collectBinaries(binaries: BinaryContainer, componentSpecs: ComponentSpecContainer) {
            for (componentSpec in componentSpecs.withType<VariantComponentSpec>(VariantComponentSpec::class.java)) {
                for (binary in componentSpec.getBinaries().withType<BinarySpecInternal>(BinarySpecInternal::class.java).values()) {
                    binaries.put(binary.projectScopedName, binary)
                }
            }
        }

        @Mutate
        fun attachBinariesToAssembleLifecycle(@Path("tasks.assemble") assemble: Task, components: ComponentSpecContainer) {
            val notBuildable: MutableList<BinarySpecInternal> = ArrayList<BinarySpecInternal>()
            var hasBuildableBinaries = false
            for (component in components.withType<VariantComponentSpec>(VariantComponentSpec::class.java)) {
                for (binary in component.getBinaries().withType<BinarySpecInternal>(BinarySpecInternal::class.java)) {
                    if (binary.isBuildable) {
                        assemble.dependsOn(binary)
                        hasBuildableBinaries = true
                    } else {
                        notBuildable.add(binary)
                    }
                }
            }
            if (!hasBuildableBinaries && !notBuildable.isEmpty()) {
                assemble.doFirst(CheckForNotBuildableBinariesAction(notBuildable))
            }
        }

        private class CheckForNotBuildableBinariesAction(private val notBuildable: MutableList<BinarySpecInternal>) : Action<Task?> {
            override fun execute(task: Task) {
                val taskDependencies = TaskDependencyUtil.getDependenciesForInternalUse(task.getTaskDependencies(), task)

                if (taskDependencies.isEmpty()) {
                    val formatter = TreeFormatter()
                    formatter.node("No buildable binaries found")
                    formatter.startChildren()
                    for (binary in notBuildable) {
                        formatter.node(binary.getDisplayName())
                        formatter.startChildren()
                        binary.buildAbility.explain(formatter)
                        formatter.endChildren()
                    }
                    formatter.endChildren()
                    throw GradleException(formatter.toString())
                }
            }
        }

        @Defaults
        fun initializeComponentSourceSets(@Each component: HasIntermediateOutputsComponentSpec, languageTransforms: LanguageTransformContainer) {
            // If there is a transform for the language into one of the component inputs, add a default source set
            for (languageTransform in languageTransforms) {
                if (component.getIntermediateTypes().contains(languageTransform.getOutputType())) {
                    component.getSources().create(languageTransform.getLanguageName(), languageTransform.getSourceSetType())
                }
            }
        }

        @Finalize
        fun applyFallbackSourceConventions(@Each languageSourceSet: LanguageSourceSet, projectIdentifier: ProjectIdentifier) {
            // Only apply default locations when none explicitly configured
            if (languageSourceSet.source.getSourceDirectories().isEmpty()) {
                val baseDir = projectIdentifier.getProjectDir()
                val defaultSourceDir =
                    Joiner.on(File.separator).skipNulls().join(baseDir.getPath(), "src", Strings.emptyToNull(languageSourceSet.parentName), Strings.emptyToNull(languageSourceSet.getName()))
                languageSourceSet.source.srcDir(defaultSourceDir)
            }
        }

        @Finalize
        fun defineBinariesCheckTasks(@Each binary: BinarySpecInternal, taskInstantiator: NamedEntityInstantiator<Task?>) {
            if (binary.isLegacyBinary) {
                return
            }
            val binaryLifecycleTask: TaskInternal = taskInstantiator.create<DefaultTask>(binary.namingScheme.getTaskName("check"), DefaultTask::class.java)
            binaryLifecycleTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP)
            binaryLifecycleTask.setDescription("Check " + binary)
            binary.setCheckTask(binaryLifecycleTask)
        }

        @Finalize
        fun copyBinariesCheckTasksToTaskContainer(tasks: TaskContainer, binaries: BinaryContainer) {
            for (binary in binaries) {
                val checkTask = binary.getCheckTask()
                if (checkTask != null) {
                    (tasks as TaskContainerInternal).addInternal(checkTask)
                }
            }
        }

        @Defaults
        fun addComponentSourcesSetsToProjectSourceSet(@Each component: SourceComponentSpec, projectSourceSet: ProjectSourceSet) {
            component.getSources().afterEach(object : Action<LanguageSourceSet?> {
                override fun execute(languageSourceSet: LanguageSourceSet?) {
                    projectSourceSet.add(languageSourceSet!!)
                }
            })
        }

        @Rules
        fun inputRules(attachInputs: AttachInputs, @Each component: GeneralComponentSpec) {
            attachInputs.binaries = component.getBinaries()
            attachInputs.sources = component.getSources()
        }

        internal abstract class AttachInputs : RuleSource() {
            @get:RuleTarget
            abstract var binaries: ModelMap<BinarySpec?>?

            @get:RuleInput
            abstract var sources: ModelMap<LanguageSourceSet?>?

            @Mutate
            fun initializeBinarySourceSets(binaries: ModelMap<BinarySpec?>) {
                // TODO - sources is not actual an input to binaries, it's an input to each binary
                binaries.withType<BinarySpecInternal?>(BinarySpecInternal::class.java, object : Action<BinarySpecInternal?> {
                    override fun execute(binary: BinarySpecInternal) {
                        binary.inputs.addAll(this.sources.values())
                    }
                })
            }
        }

        @Hidden
        @Model
        fun dependentBinariesResolver(instantiator: Instantiator): DependentBinariesResolver {
            return instantiator.newInstance<DefaultDependentBinariesResolver>(DefaultDependentBinariesResolver::class.java)
        }

        @Defaults
        fun registerBaseDependentBinariesResolutionStrategy(resolver: DependentBinariesResolver, serviceRegistry: ServiceRegistry?) {
            resolver.register(BaseDependentBinariesResolutionStrategy())
        }
    }
}
