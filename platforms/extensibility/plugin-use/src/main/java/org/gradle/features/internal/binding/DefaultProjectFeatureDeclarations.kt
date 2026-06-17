/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.features.internal.binding

import com.google.common.base.Function
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemReporterInternal
import org.gradle.api.problems.internal.ProblemSpecInternal
import org.gradle.api.reflect.TypeOf
import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.TargetTypeInformation
import org.gradle.features.internal.binding.TargetTypeInformationChecks.isOverlappingBindingType
import org.gradle.internal.Pair
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer
import java.util.Objects
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Default implementation of [ProjectFeatureDeclarations] that registers project types.
 */
class DefaultProjectFeatureDeclarations(
    @field:Suppress("unused") private val inspectionScheme: InspectionScheme,
    private val instantiator: Instantiator,
    private val problemReporter: ProblemReporterInternal
) : ProjectFeatureDeclarations {
    private val pluginClasses: MutableMap<RegisteringPluginKey, MutableSet<Class<out Plugin<Project>>>> = LinkedHashMap<RegisteringPluginKey, MutableSet<Class<out Plugin<Project>>>>()

    private var projectFeatureImplementations: MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>>? = null

    override fun addDeclaration(pluginId: String?, pluginClass: Class<out Plugin<Project>>, registeringPluginClass: Class<out Plugin<Settings>>) {
        check(projectFeatureImplementations == null) { "Cannot register a plugin after project types have been discovered" }
        val pluginKey = RegisteringPluginKey(registeringPluginClass, pluginId)
        pluginClasses.computeIfAbsent(pluginKey) { k: RegisteringPluginKey? -> LinkedHashSet<Class<out Plugin<Project?>?>?>() }.add(pluginClass)
    }

    private fun discoverProjectFeatureImplementations(): MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>> {
        val projectFeatureDeclarations: MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>> = LinkedHashMap<String, MutableSet<ProjectFeatureImplementation<*, *>>>()
        pluginClasses.forEach { (registeringPluginClass: RegisteringPluginKey?, registeredPluginClasses: MutableSet<Class<out Plugin<Project?>>?>?) ->
            registeredPluginClasses!!.forEach(Consumer { pluginClass: Class<out Plugin<Project?>>? ->
                val pluginClassTypeMetadata = inspectionScheme.getMetadataStore().getTypeMetadata(pluginClass)
                val pluginClassAnnotationMetadata = pluginClassTypeMetadata.getTypeAnnotationMetadata()
                registerTypeIfPresent(registeringPluginClass!!, pluginClass, pluginClassAnnotationMetadata, projectFeatureDeclarations)
                registerFeaturesIfPresent(registeringPluginClass, pluginClass, pluginClassAnnotationMetadata, projectFeatureDeclarations)
            })
        }
        return ImmutableMap.copyOf<String, MutableSet<ProjectFeatureImplementation<*, *>>>(projectFeatureDeclarations)
    }

    private fun <T : Definition<V?>?, V : BuildModel?> registerFeature(
        registeringPlugin: RegisteringPluginKey,
        pluginClass: Class<out Plugin<Project>>,
        binding: ProjectFeatureBindingDeclaration<T?, V?>,
        projectFeatureDeclarations: MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>>
    ) {
        val projectFeatureName = binding.getName()

        if (binding.getDefinitionSafety() == ProjectFeatureBindingDeclaration.Safety.SAFE) {
            validateDefinitionSafety(binding)
        }

        if (binding.targetDefinitionType() is TargetTypeInformation.BuildModelTargetTypeInformation<*> &&
            (binding.targetDefinitionType() as TargetTypeInformation.BuildModelTargetTypeInformation<*>).getBuildModelType() == BuildModel.None::class.java
        ) {
            val bindingTypeProblem = problemReporter.internalCreate(Action { builder: ProblemSpecInternal? ->
                builder!!
                    .id("bind=to-build-model-none", "Project features binds to BuildModel.None", GradleCoreProblemGroup.configurationUsage())
                    .details("A project feature cannot bind to 'BuildModel.None' as its target build model type.")
                    .contextualLabel("Project feature '" + projectFeatureName + "' is bound to 'BuildModel.None'")
                    .solution("Bind to a target definition type instead.")
                    .solution("Bind to a concrete build model type other than 'BuildModel.None'.")
            }
            )

            throwTypeValidationException("Project feature '" + projectFeatureName + "' is bound to an invalid type:", mutableListOf<ProblemInternal>(bindingTypeProblem))
        }

        val implementations = projectFeatureDeclarations.computeIfAbsent(
            projectFeatureName
        ) { k: String? -> LinkedHashSet<ProjectFeatureImplementation<*, *>?>() }

        val existingPluginClasses =
            implementations.stream().filter { existingFeature: ProjectFeatureImplementation<*, *>? ->  // If the feature name matches another registration, check if the target types are ambiguous
                existingFeature!!.getFeatureName() == binding.getName() && isOverlappingBindingType(existingFeature.getTargetDefinitionType(), binding.targetDefinitionType())
            }
                .map { obj: ProjectFeatureImplementation<*, *>? -> obj!!.getPluginClass() }
                .collect(Collectors.toList())

        if (!existingPluginClasses.isEmpty()) {
            val problems: MutableList<ProblemInternal> = ArrayList<ProblemInternal>()
            existingPluginClasses.forEach(Consumer { existingPluginClass: Class<out Plugin<Project?>?>? ->
                problems.add(
                    problemReporter.internalCreate(Action { builder: ProblemSpecInternal? ->
                        builder!!
                            .id("duplicate-project-feature-registration", "Duplicate project feature registration", GradleCoreProblemGroup.configurationUsage())
                            .details("A project feature or type with a given name must bind to a unique target type.")
                            .contextualLabel("Project feature '" + projectFeatureName + "' is registered by both '" + pluginClass.getName() + "' and '" + existingPluginClass!!.getName() + "' but their bindings have overlapping target types.")
                            .solution("Remove one of the plugins from the build.")
                    }
                    )
                )
            })

            throwTypeValidationException("Project feature '" + projectFeatureName + "' is registered by multiple plugins:", problems)
        }

        implementations.add(
            DefaultProjectFeatureImplementation<T?, V?>(
                projectFeatureName,
                binding.getDefinitionType(),
                binding.getDefinitionImplementationType().orElse(binding.getDefinitionType()),
                binding.getDefinitionSafety(),
                binding.getApplyActionSafety(),
                binding.targetDefinitionType(),
                binding.getBuildModelType(),
                binding.getBuildModelImplementationType().orElse(binding.getBuildModelType()),
                binding.getNestedBuildModelTypes(),
                pluginClass,
                registeringPlugin.pluginClass,
                registeringPlugin.pluginId,
                binding.getApplyActionFactory()
            )
        )
    }

    private fun registerFeaturesIfPresent(
        registeringPluginClass: RegisteringPluginKey,
        pluginClass: Class<out Plugin<Project>>,
        pluginClassAnnotationMetadata: TypeAnnotationMetadata,
        projectFeatureDeclarations: MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>>
    ) {
        val bindsProjectFeatureAnnotation = pluginClassAnnotationMetadata.getAnnotation<BindsProjectFeature>(BindsProjectFeature::class.java)
        if (bindsProjectFeatureAnnotation.isPresent()) {
            val bindsSoftwareType = bindsProjectFeatureAnnotation.get()
            val bindingRegistrationClass: Class<out ProjectFeatureBinding> = bindsSoftwareType.value
            val bindingRegistration = instantiator.newInstance(bindingRegistrationClass)
            val builder: ProjectFeatureBindingBuilderInternal = DefaultProjectFeatureBindingBuilder()
            bindingRegistration.bind(builder)
            builder.build().forEach(Consumer { binding: ProjectFeatureBindingDeclaration<*, *>? -> registerFeature(registeringPluginClass, pluginClass, binding, projectFeatureDeclarations) }
            )
        }
    }

    private fun registerTypeIfPresent(
        registeringPluginKey: RegisteringPluginKey,
        pluginClass: Class<out Plugin<Project>>,
        pluginClassAnnotationMetadata: TypeAnnotationMetadata,
        projectFeatureDeclarations: MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>>
    ) {
        val bindsProjectTypeAnnotation = pluginClassAnnotationMetadata.getAnnotation<BindsProjectType>(BindsProjectType::class.java)
        if (bindsProjectTypeAnnotation.isPresent()) {
            val bindsProjectType = bindsProjectTypeAnnotation.get()
            val bindingRegistrationClass: Class<out ProjectTypeBinding> = bindsProjectType.value
            val bindingRegistration = instantiator.newInstance(bindingRegistrationClass)
            val builder: ProjectTypeBindingBuilderInternal = DefaultProjectTypeBindingBuilder()
            bindingRegistration.bind(builder)
            builder.build().forEach(Consumer { binding: ProjectFeatureBindingDeclaration<*, *>? -> registerFeature(registeringPluginKey, pluginClass, binding, projectFeatureDeclarations) }
            )
        }
    }

    private fun validateDefinitionSafety(binding: ProjectFeatureBindingDeclaration<*, *>) {
        val problems: MutableList<ProblemInternal> = ArrayList<ProblemInternal>()
        if (binding.getDefinitionImplementationType().isPresent() && binding.getDefinitionImplementationType().get() != binding.getDefinitionType()) {
            problems.add(problemReporter.internalCreate(Action { builder: ProblemSpecInternal? ->
                builder!!
                    .id("unsafe-definition-implementation-type", "Definition implementation type specified for safe definition", GradleCoreProblemGroup.configurationUsage())
                    .details("Safe definitions must not specify an implementation type.")
                    .contextualLabel(
                        "Project feature '" + binding.getName() + "' has a definition with type '" + binding.getDefinitionType()
                            .getSimpleName() + "' which was declared safe but has an implementation type '" + binding.getDefinitionImplementationType().get().getSimpleName() + "'"
                    )
                    .solution("Mark the definition as unsafe.")
                    .solution("Remove the implementation type specification.")
            }
            ))
        }

        problemReporter.report(problems)

        throwTypeValidationException("Project feature '" + binding.getName() + "' has a definition type which was declared safe but has the following issues:", problems)
    }

    override fun getProjectFeatureImplementations(): MutableMap<String, MutableSet<ProjectFeatureImplementation<*, *>>> {
        if (projectFeatureImplementations == null) {
            projectFeatureImplementations = discoverProjectFeatureImplementations()
        }
        return projectFeatureImplementations!!
    }

    override fun getSchema(): NamedDomainObjectCollectionSchema {
        return NamedDomainObjectCollectionSchema {
            Iterables.transform<Any, ProjectFeatureSchema>(
                Iterable {
                    getProjectFeatureImplementations().entries.stream().flatMap<Any> { entry: MutableMap.MutableEntry<String?, MutableSet<ProjectFeatureImplementation<*, *>?>?>? ->
                        entry!!.value!!.stream().map<Any> { impl: ProjectFeatureImplementation<*, *>? -> Pair.of(entry.key, impl) }
                    }.iterator()
                },
                Function { pair: Any -> ProjectFeatureSchema(pair.left, pair.right.getDefinitionPublicType()) }
            )
        }
    }

    private class ProjectFeatureSchema(private val name: String, private val modelPublicType: Class<*>) : NamedDomainObjectCollectionSchema.NamedDomainObjectSchema {
        override fun getName(): String {
            return name
        }

        override fun getPublicType(): TypeOf<*> {
            return TypeOf.typeOf(modelPublicType)
        }
    }

    class RegisteringPluginKey(val pluginClass: Class<out Plugin<Settings>>, val pluginId: String?) {
        override fun equals(o: Any): Boolean {
            if (o !is RegisteringPluginKey) {
                return false
            }
            val pluginKey = o
            return pluginClass == pluginKey.pluginClass && pluginId == pluginKey.pluginId
        }

        override fun hashCode(): Int {
            return Objects.hash(pluginClass, pluginId)
        }
    }

    companion object {
        private fun throwTypeValidationException(summary: String, problems: MutableList<ProblemInternal>) {
            val formattedErrors = problems.stream()
                .map<String> { problem: ProblemInternal? -> TypeValidationProblemRenderer.renderMinimalInformationAbout(problem) }
                .collect(Collectors.toList())

            if (!formattedErrors.isEmpty()) {
                val formatter = TreeFormatter(true)
                formatter.node(summary)
                formatter.startChildren()
                formattedErrors.forEach(Consumer { text: String? -> formatter.node(text) })
                formatter.endChildren()
                throw IllegalArgumentException(formatter.toString())
            }
        }
    }
}
