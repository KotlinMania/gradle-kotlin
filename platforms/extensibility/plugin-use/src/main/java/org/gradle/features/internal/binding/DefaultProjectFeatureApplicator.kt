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

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemReporterInternal
import org.gradle.api.problems.internal.ProblemSpecInternal
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Nested
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.BuildModelRegistrar
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.internal.file.DefaultProjectFeatureLayout
import org.gradle.features.internal.registration.DefaultConfigurationRegistrar
import org.gradle.features.internal.registration.DefaultTaskRegistrar
import org.gradle.features.registration.ConfigurationRegistrar
import org.gradle.features.registration.TaskRegistrar
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata
import org.gradle.internal.reflect.annotations.TypeAnnotationMetadataStore
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.ServiceLookupException
import org.gradle.internal.service.UnknownServiceException
import java.lang.reflect.Type
import java.util.function.Consumer
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Applies project features to a target object by registering the feature application as a child of the target object (unless
 * configured otherwise) and performing validations.  Application returns the public model object of the feature.  Features
 * are applied only once per target object and always return the same public model object for a given target/feature
 * combination.
 */
abstract class DefaultProjectFeatureApplicator @Inject constructor(
    private val classLoaderScope: ClassLoaderScope,
    private val projectObjectFactory: ObjectFactory,
    private val problemReporter: ProblemReporterInternal,
    private val allServices: ServiceLookup
) : ProjectFeatureApplicator {
    private val propertyWalker: PropertyWalker = DefaultPropertyWalker(
        this.typeAnnotationMetadataStore
    )
    private val pendingFeatureApplications: MutableList<ProjectFeatureApplicator.FeatureApplication<*, *>> = ArrayList<ProjectFeatureApplicator.FeatureApplication<*, *>>()

    private fun <OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?> instantiateDefinition(
        parentDefinition: DynamicObjectAware,
        projectFeature: ProjectFeatureImplementation<OwnDefinition?, OwnBuildModel?>,
        parentDefinitionContext: ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext
    ): ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult<OwnDefinition?, OwnBuildModel?> {
        return parentDefinitionContext.getOrAddChildDefinition<OwnDefinition?, OwnBuildModel?>(projectFeature, Supplier {
            if (parentDefinition is Project) {
                checkSingleProjectTypeApplication<OwnDefinition?, OwnBuildModel?>(parentDefinitionContext, projectFeature)
            }
            this.pluginManager.apply(projectFeature.getPluginClass())
            instantiateBoundFeatureObjects<OwnDefinition?, OwnBuildModel?>(parentDefinition, projectFeature)
        })
    }

    override fun <OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?> registerFeatureApplicationFor(
        parentDefinition: DynamicObjectAware,
        projectFeature: ProjectFeatureImplementation<OwnDefinition?, OwnBuildModel?>
    ): ProjectFeatureApplicator.FeatureApplication<OwnDefinition?, OwnBuildModel?> {
        val parentDefinitionContext = ProjectFeatureSupportInternal.getContext(parentDefinition)

        val result = instantiateDefinition<OwnDefinition?, OwnBuildModel?>(parentDefinition, projectFeature, parentDefinitionContext)

        if (result.isNew) {
            pendingFeatureApplications.add(result.featureApplication)
            val plugin: Plugin<Project> = this.pluginManager.getPluginContainer().getPlugin(projectFeature.getPluginClass())
            this.modelDefaultsApplicator!!.applyDefaultsTo(parentDefinition, result.featureApplication.getDefinitionInstance()!!, ClassLoaderContextFromScope(classLoaderScope), plugin, projectFeature)
        }

        return result.featureApplication
    }

    override fun applyFeatures() {
        pendingFeatureApplications.forEach(Consumer { obj: ProjectFeatureApplicator.FeatureApplication<*, *>? -> obj!!.apply() })
    }

    private fun <OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?> instantiateBoundFeatureObjects(
        parentDefinition: Any,
        projectFeature: ProjectFeatureImplementation<OwnDefinition?, OwnBuildModel?>
    ): ProjectFeatureApplicator.FeatureApplication<OwnDefinition?, OwnBuildModel?> {
        // Context-specific services for this feature binding
        val featureServices = getContextSpecificServiceLookup<OwnDefinition?, OwnBuildModel?>(projectFeature)
        val featureObjectFactory = this.objectFactoryFactory.createObjectFactory(featureServices)
        val buildModelRegistrar: BuildModelRegistrarInternal = DefaultBuildModelRegistrar(
            featureObjectFactory, this,
            this.projectFeatureDeclarations!!
        )
        if (featureServices is UnsafeServicesForApplyAction) {
            featureServices.setBuildModelRegistrar(buildModelRegistrar)
        }

        // Instantiate the definition and build model objects with the feature-specific object factory
        val definition = featureObjectFactory.newInstance(projectFeature.getDefinitionImplementationType())
        val buildModelInstance: OwnBuildModel? = ProjectFeatureSupportInternal.createBuildModelInstance<OwnDefinition?, OwnBuildModel?>(featureObjectFactory, projectFeature)
        ProjectFeatureSupportInternal.attachDefinitionContext<OwnBuildModel?>(
            definition, buildModelInstance, this,
            this.projectFeatureDeclarations, featureObjectFactory
        )

        // Construct an apply action context with the feature-specific object factory
        val applyActionContext: ProjectFeatureApplicationContext =
            projectObjectFactory.newInstance<DefaultProjectFeatureApplicationContextInternal>(DefaultProjectFeatureApplicationContextInternal::class.java)

        // bind any nested definitions to build model instances
        bindNestedDefinitions(projectFeature.getDefinitionPublicType(), uncheckedCast<DynamicObjectAware?>(definition)!!, buildModelRegistrar, projectFeature.getNestedBuildModelTypes())

        return DefaultFeatureApplication<OwnDefinition?, OwnBuildModel?>(
            projectFeature.getDefinitionImplementationType(),
            definition,
            buildModelInstance,
            parentDefinition,
            applyActionContext,
            projectFeature.getApplyActionFactory().create(featureObjectFactory),
            propertyWalker
        )
    }

    private fun bindNestedDefinitions(
        publicType: Class<*>,
        parent: DynamicObjectAware,
        buildModelRegistrar: BuildModelRegistrarInternal,
        buildModelImplementationTypes: MutableMap<Class<*>, Class<*>>
    ) {
        // Must use an anonymous class for config cache compatibility
        propertyWalker.walkProperties(publicType, parent, object : PropertyWalker.Visitor {
            override fun visit(propertyMetadata: PropertyAnnotationMetadata, propertyValue: Any) {
                if (Definition::class.java.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                    bindNestedDefinition(propertyValue, buildModelRegistrar, buildModelImplementationTypes)
                }
                if (NamedDomainObjectContainer::class.java.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                    val ndoc: NamedDomainObjectContainer<*> = uncheckedCast<NamedDomainObjectContainer<*>?>(propertyValue)!!
                    val elementType: Class<*> = (ndoc as AbstractNamedDomainObjectContainer<*>).getType()
                    if (Definition::class.java.isAssignableFrom(elementType)) {
                        // Must use an anonymous class for config cache compatibility
                        ndoc.all(object : Action<Any> {
                            override fun execute(element: Any) {
                                bindNestedDefinition(element, buildModelRegistrar, buildModelImplementationTypes)
                            }
                        })
                    }
                }
            }
        })
    }

    private fun <OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?> getContextSpecificServiceLookup(projectFeature: ProjectFeatureImplementation<OwnDefinition?, OwnBuildModel?>): ServicesForApplyAction {
        val taskRegistrar: TaskRegistrar = DefaultTaskRegistrar(this.taskContainer)
        val projectFeatureLayout: ProjectFeatureLayout = DefaultProjectFeatureLayout(this.projectLayout)
        val configurationRegistrar: ConfigurationRegistrar = DefaultConfigurationRegistrar(this.configurationContainer)

        // Construct an object factory that provides the appropriate services during apply action execution
        return if (projectFeature.getApplyActionSafety() == ProjectFeatureBindingDeclaration.Safety.SAFE)
            SafeServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar, projectFeature.getFeatureName(), problemReporter)
        else
            UnsafeServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar, projectFeature.getFeatureName(), problemReporter)
    }

    @get:Inject
    protected abstract val taskContainer: TaskContainer?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    @get:Inject
    protected abstract val configurationContainer: ConfigurationContainer?

    @get:Inject
    protected abstract val pluginManager: PluginManagerInternal?

    @get:Inject
    protected abstract val modelDefaultsApplicator: ModelDefaultsApplicator?

    @get:Inject
    protected abstract val objectFactoryFactory: ObjectFactoryFactory?

    @get:Inject
    protected abstract val projectFeatureDeclarations: ProjectFeatureDeclarations?

    @get:Inject
    protected abstract val typeAnnotationMetadataStore: TypeAnnotationMetadataStore?

    /**
     * Walks the public properties of a given object, visiting each property and descending into any properties that are annotated with [Nested].
     *
     * Ignores any properties that are null, although properties whose value is null will be visited (e.g. ignores getFoo() == null, but will visit
     * getFoo().getOrNull() == null)
     */
    private class DefaultPropertyWalker(private val typeAnnotationMetadataStore: TypeAnnotationMetadataStore) : PropertyWalker {
        override fun walkProperties(publicType: Class<*>, parent: Any, visitor: PropertyWalker.Visitor) {
            typeAnnotationMetadataStore.getTypeAnnotationMetadata(publicType).getPropertiesAnnotationMetadata().forEach(Consumer { propertyMetadata: PropertyAnnotationMetadata? ->
                val propertyValue = propertyMetadata!!.getPropertyValue(parent)
                if (propertyValue != null) {
                    visitor.visit(propertyMetadata, propertyValue)

                    if (propertyMetadata.isAnnotationPresent(Nested::class.java)) {
                        walkProperties(propertyMetadata.getDeclaredReturnType().getRawType(), uncheckedCast<Any?>(propertyValue)!!, visitor)
                    }
                    if (DomainObjectCollection::class.java.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                        val collection: DomainObjectCollection<*> = uncheckedCast<DomainObjectCollection<*>?>(propertyValue)!!
                        // Must use an anonymous class for config cache compatibility
                        collection.all(object : Action<Any> {
                            override fun execute(element: Any) {
                                walkProperties(element.javaClass, element, visitor)
                            }
                        })
                    }
                }
            })
        }
    }

    private interface PropertyWalker {
        fun walkProperties(publicType: Class<*>, parent: Any, visitor: Visitor)
        interface Visitor {
            fun visit(propertyMetadata: PropertyAnnotationMetadata, propertyValue: Any)
        }
    }

    private class DefaultFeatureApplication<OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?>(
        private val definitionImplementationType: Class<out OwnDefinition>,
        private val definitionInstance: OwnDefinition?,
        private val buildModelInstance: OwnBuildModel?,
        parentDefinition: Any,
        private val context: ProjectFeatureApplicationContext,
        private val applyAction: ProjectFeatureApplyAction<OwnDefinition?, OwnBuildModel?, *>,
        private val propertyWalker: PropertyWalker
    ) : ProjectFeatureApplicator.FeatureApplication<OwnDefinition?, OwnBuildModel?> {
        private val parentDefinition: Any
        private val isProjectType: Boolean
        private var applied = false

        init {
            this.isProjectType = parentDefinition is Project
            this.parentDefinition = if (isProjectType) PROJECT else parentDefinition
        }

        override fun getDefinitionInstance(): OwnDefinition? {
            return definitionInstance
        }

        override fun isProjectType(): Boolean {
            return isProjectType
        }

        override fun apply() {
            if (!applied) {
                finalizeProperties()
                applyAction.apply(context, definitionInstance, buildModelInstance, uncheckedCast(parentDefinition))
                applied = true
            }
        }

        fun finalizeProperties() {
            // // Must use an anonymous class for config cache compatibility
            propertyWalker.walkProperties(definitionImplementationType, definitionInstance!!, object : PropertyWalker.Visitor {
                override fun visit(propertyMetadata: PropertyAnnotationMetadata, propertyValue: Any) {
                    if (Property::class.java.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                        (propertyValue as Property<*>).finalizeValue()
                    }
                    if (NamedDomainObjectContainer::class.java.isAssignableFrom(propertyMetadata.getDeclaredReturnType().getRawType())) {
                        val ndoc: NamedDomainObjectContainer<*> = uncheckedCast<NamedDomainObjectContainer<*>?>(propertyValue)!!
                        ndoc.disallowChanges()
                    }
                }
            })
        }

        companion object {
            // We don't want to store the Project object because of configuration cache compatibility, so we use a placeholder
            private val PROJECT = Any()
        }
    }

    /**
     * The internal implementation of the context passed to project feature apply actions, exposing an object factory
     * appropriate for the configured safety of the apply action.
     */
    internal abstract class DefaultProjectFeatureApplicationContextInternal @Inject constructor() : ProjectFeatureApplicationContext {
        override fun <T : Definition<V?>?, V : BuildModel?> getBuildModel(definition: T?): V? {
            return uncheckedNonnullCast<V?>(ProjectFeatureSupportInternal.getContext(definition as DynamicObjectAware).getBuildModel())
        }
    }

    private class ClassLoaderContextFromScope(private val scope: ClassLoaderScope) : ModelDefaultsApplicator.ClassLoaderContext {
        override fun getClassLoader(): ClassLoader {
            return scope.getLocalClassLoader()
        }

        override fun getParentClassLoader(): ClassLoader {
            return scope.getParent().getLocalClassLoader()
        }
    }

    /**
     * A base class for limited service lookups for use during feature apply actions.  Provides context-specific services
     * unique to the feature binding as well as a small set of services used by Gradle internals.
     */
    private abstract class ServicesForApplyAction(
        protected val allServices: ServiceLookup,
        protected val taskRegistrar: TaskRegistrar,
        protected val projectFeatureLayout: ProjectFeatureLayout,
        protected val configurationRegistrar: ConfigurationRegistrar
    ) : ServiceLookup {
        @Throws(ServiceLookupException::class)
        override fun find(serviceType: Type): Any? {
            if (serviceType is Class<*>) {
                val serviceClass = uncheckedNonnullCast<Class<*>?>(serviceType)
                // Context-specific services unique to this feature binding
                if (serviceClass!!.isAssignableFrom(TaskRegistrar::class.java)) {
                    return taskRegistrar
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureLayout::class.java)) {
                    return projectFeatureLayout
                }
                if (serviceClass.isAssignableFrom(ConfigurationRegistrar::class.java)) {
                    return configurationRegistrar
                }
                // Services used by Gradle internals
                if (serviceClass.isAssignableFrom(ManagedObjectRegistry::class.java)) {
                    return allServices.find(ManagedObjectRegistry::class.java)
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureDeclarations::class.java)) {
                    return allServices.find(ProjectFeatureDeclarations::class.java)
                }
                if (serviceClass.isAssignableFrom(ProjectFeatureApplicator::class.java)) {
                    return allServices.find(ProjectFeatureApplicator::class.java)
                }
                if (serviceClass.isAssignableFrom(TaskDependencyFactory::class.java)) {
                    return allServices.find(TaskDependencyFactory::class.java)
                }
                if (serviceClass.isAssignableFrom(InstantiatorFactory::class.java)) {
                    return allServices.find(InstantiatorFactory::class.java)
                }
                // None of the above
                return null
            }
            return null
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type): Any {
            val result = find(serviceType)
            if (result == null) {
                return notFound(serviceType)
            }
            return result
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type, annotatedWith: Class<out Annotation>): Any {
            return notFound(serviceType)
        }

        protected abstract fun notFound(serviceType: Type): Any
    }

    /**
     * A limited service lookup for use during feature apply actions, exposing both safe and unsafe services.
     */
    private class UnsafeServicesForApplyAction(
        allServices: ServiceLookup,
        taskRegistrar: TaskRegistrar,
        projectFeatureLayout: ProjectFeatureLayout,
        configurationRegistrar: ConfigurationRegistrar,
        private val featureName: String, // Not used in this class
        private val problemReporter: ProblemReporterInternal
    ) : ServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar) {
        private var buildModelRegistrar: BuildModelRegistrarInternal? = null // set after construction to share ObjectFactory created with this instance

        fun setBuildModelRegistrar(buildModelRegistrar: BuildModelRegistrarInternal) {
            this.buildModelRegistrar = buildModelRegistrar
        }

        @Throws(ServiceLookupException::class)
        override fun find(serviceType: Type): Any? {
            val found = super.find(serviceType)
            if (found != null) {
                return found
            }

            // Context-specific service for build model registration, only available in unsafe apply actions
            if (serviceType is Class<*>) {
                val serviceClass = uncheckedNonnullCast<Class<*>?>(serviceType)
                if (serviceClass!!.isAssignableFrom(BuildModelRegistrar::class.java)) {
                    return buildModelRegistrar
                }
            }

            // If not found in the safe/limited set, allow lookup from all services
            return allServices.find(serviceType)
        }

        override fun notFound(serviceType: Type): Any? {
            val problem = problemReporter.internalCreate(Action { builder: ProblemSpecInternal? ->
                builder!!
                    .id("unsafe-apply-action-uses-unknown-service", "An unsafe apply action is attempting to use an unknown service", GradleCoreProblemGroup.configurationUsage())
                    .contextualLabel("Project feature '" + featureName + "' has an apply action that attempts to inject an unknown service with type '" + serviceType.getTypeName() + "'.")
                    .details("Services of type " + serviceType.getTypeName() + " are not available for injection into project feature apply actions.")
                    .solution("Remove the '" + serviceType.getTypeName() + "' injection from the apply action.")
            }
            )
            problemReporter.reportError(problem)
            throw UnknownServiceException(serviceType, TypeValidationProblemRenderer.renderMinimalInformationAbout(problem))
        }
    }

    /**
     * A limited service lookup for use during safe feature apply actions, exposing only a small set of safe services.
     */
    private class SafeServicesForApplyAction(
        allServices: ServiceLookup,
        taskRegistrar: TaskRegistrar,
        projectFeatureLayout: ProjectFeatureLayout,
        configurationRegistrar: ConfigurationRegistrar,
        private val featureName: String,
        private val problemReporter: ProblemReporterInternal
    ) : ServicesForApplyAction(allServices, taskRegistrar, projectFeatureLayout, configurationRegistrar) {
        @Throws(ServiceLookupException::class)
        override fun find(serviceType: Type): Any? {
            val found = super.find(serviceType)
            if (found != null) {
                return found
            }

            // Only allow access to a small set of safe services from the parent services
            val serviceClass = uncheckedNonnullCast<Class<*>?>(serviceType)
            for (safeService in SAFE_APPLY_ACTION_SERVICES) {
                if (serviceClass!!.isAssignableFrom(safeService)) {
                    return allServices.find(serviceType)
                }
            }

            return null
        }

        override fun notFound(serviceType: Type): Any? {
            val problem = problemReporter.internalCreate(Action { builder: ProblemSpecInternal? ->
                builder!!
                    .id("safe-apply-action-uses-unsafe-service", "A safe apply action is attempting to use an unsafe service", GradleCoreProblemGroup.configurationUsage())
                    .contextualLabel("Project feature '" + featureName + "' has a safe apply action that attempts to inject an unsafe service with type '" + serviceType.getTypeName() + "'.")
                    .details(safeServicesListExplanation)
                    .solution("Mark the apply action as unsafe.")
                    .solution("Remove the '" + serviceType.getTypeName() + "' injection from the apply action.")
            }
            )
            problemReporter.reportError(problem)
            throw UnknownServiceException(serviceType, TypeValidationProblemRenderer.renderMinimalInformationAbout(problem))
        }

        companion object {
            private val safeServicesListExplanation: String
                get() {
                    val formatter = TreeFormatter(true)
                        .node("Only the following services are available in safe apply actions")
                    formatter.startChildren()
                    for (safeService in SAFE_APPLY_ACTION_SERVICES) {
                        formatter.node("").appendType(safeService)
                    }
                    formatter.endChildren()
                    return formatter.toString()
                }
        }
    }

    companion object {
        private fun <OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?> checkSingleProjectTypeApplication(
            context: ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext,
            projectFeature: ProjectFeatureImplementation<OwnDefinition?, OwnBuildModel?>
        ) {
            context.childFeatures().keys.stream().findFirst().ifPresent(Consumer { projectTypeAlreadyApplied: ProjectFeatureImplementation<*, *>? ->
                throw IllegalStateException(
                    "The project has already applied the '" +
                            projectTypeAlreadyApplied!!.getFeatureName() +
                            "' project type and is also attempting to apply the '" +
                            projectFeature.getFeatureName() +
                            "' project type.  Only one project type can be applied to a project."
                )
            })
        }

        private fun bindNestedDefinition(propertyValue: Any, buildModelRegistrar: BuildModelRegistrarInternal, buildModelImplementationTypes: MutableMap<Class<*>, Class<*>>) {
            val nestedDefinition: Definition<*> = uncheckedCast<Definition<*>?>(propertyValue)!!
            buildModelRegistrar.registerBuildModel(nestedDefinition, buildModelImplementationTypes)
        }

        /**
         * The set of services that are considered safe for use during safe apply actions.
         */
        private val SAFE_APPLY_ACTION_SERVICES: MutableSet<Class<*>> = ImmutableSet.of<Class<*>>(
            TaskRegistrar::class.java,
            ProjectFeatureLayout::class.java,
            ConfigurationRegistrar::class.java,
            ObjectFactory::class.java,
            ProviderFactory::class.java,
            DependencyFactory::class.java
        )
    }
}
