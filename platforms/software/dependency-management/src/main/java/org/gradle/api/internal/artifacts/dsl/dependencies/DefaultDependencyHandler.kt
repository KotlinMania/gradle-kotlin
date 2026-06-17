/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.ExternalModuleDependencyVariantSpec
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.HasConfigurableAttributes
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.dependencies.AbstractExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependencyVariant
import org.gradle.api.internal.artifacts.dsl.DependencyHandlerInternal
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.internal.metaobject.MethodMixIn
import org.gradle.util.internal.ConfigureUtil
import javax.inject.Inject

abstract class DefaultDependencyHandler(
    private val configurationContainer: ConfigurationContainer,
    private val dependencyFactory: DependencyFactoryInternal,
    private val dependencyConstraintHandler: DependencyConstraintHandler,
    private val componentMetadataHandler: ComponentMetadataHandler,
    private val componentModuleMetadataHandler: ComponentModuleMetadataHandler,
    private val resolutionQueryFactory: ArtifactResolutionQueryFactory,
    private val attributesSchema: AttributesSchema,
    private val transforms: VariantTransformRegistry,
    private val artifactTypeContainer: ArtifactTypeRegistry,
    private val objects: ObjectFactory,
    private val platformSupport: PlatformSupport
) : DependencyHandlerInternal, MethodMixIn {
    private val dynamicMethods: DynamicAddDependencyMethods

    init {
        configureSchema()
        dynamicMethods = DynamicAddDependencyMethods(configurationContainer, DefaultDependencyHandler.DirectDependencyAdder())
    }

    override fun add(configurationName: String, dependencyNotation: Any): Dependency {
        return add(configurationName, dependencyNotation, null)
    }

    override fun add(configurationName: String, dependencyNotation: Any, configureClosure: Closure<*>?): Dependency {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, configureClosure)!!
    }

    override fun <T, U : ExternalModuleDependency?> addProvider(configurationName: String, dependencyNotation: Provider<T?>, configuration: Action<in U?>) {
        doAddProvider(configurationContainer.getByName(configurationName), dependencyNotation, closureOf(configuration))
    }

    override fun <T> addProvider(configurationName: String, dependencyNotation: Provider<T?>) {
        TODO(
            """
            |Cannot convert element
            |With text:
            |T, ExternalModuleDependency><T, ExternalModuleDependency>addProvider(configurationName, dependencyNotation, Actions.<Object>doNothing());
            """.trimMargin()
        )
    }

    override fun <T, U : ExternalModuleDependency?> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T?>, configuration: Action<in U?>) {
        addProvider(configurationName, dependencyNotation.asProvider(), configuration)
    }

    override fun <T> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T?>) {
        TODO(
            """
            |Cannot convert element
            |With text:
            |T, ExternalModuleDependency><T, ExternalModuleDependency>addProviderConvertible(configurationName, dependencyNotation, Actions.<Object>doNothing());
            """.trimMargin()
        )
    }

    private fun <U : ExternalModuleDependency?> closureOf(configuration: Action<in U?>): Closure<Any> {
        return object : Closure<Any>(this, this) {
            override fun call(): Any {
                configuration.execute(uncheckedCast(getDelegate()))
                return null
            }

            override fun call(arguments: Any): Any {
                configuration.execute(uncheckedCast(arguments))
                return null
            }
        }
    }

    override fun create(dependencyNotation: Any): Dependency {
        return create(dependencyNotation, null)
    }

    override fun create(dependencyNotation: Any, configureClosure: Closure<*>?): Dependency {
        val dependency = dependencyFactory.createDependency(dependencyNotation)
        return ConfigureUtil.configure<Dependency>(configureClosure, dependency)
    }

    private fun doAdd(configuration: Configuration, dependencyNotation: Any, configureClosure: Closure<*>?): Dependency? {
        if (dependencyNotation is Configuration) {
            throw GradleException("Adding a Configuration as a dependency is no longer allowed as of Gradle 8.0.")
        } else if (dependencyNotation is ProviderConvertible<*>) {
            return doAdd(configuration, dependencyNotation.asProvider(), configureClosure)
        } else if (dependencyNotation is ProviderInternal<*>) {
            val provider = dependencyNotation
            if (provider.getType() != null && ExternalModuleDependencyBundle::class.java.isAssignableFrom(provider.getType())) {
                val bundle: ExternalModuleDependencyBundle = uncheckedCast<ExternalModuleDependencyBundle?>(provider.get())!!
                for (dependency in bundle) {
                    doAddRegularDependency(configuration, dependency, configureClosure!!)
                }
                return null
            }
            return doAddProvider(configuration, provider, configureClosure!!)
        } else if (dependencyNotation is Provider<*>) {
            return doAddProvider(configuration, dependencyNotation, configureClosure!!)
        } else {
            return doAddRegularDependency(configuration, dependencyNotation, configureClosure!!)
        }
    }

    private fun doAddRegularDependency(configuration: Configuration, dependencyNotation: Any, configureClosure: Closure<*>): Dependency {
        val dependency = create(dependencyNotation, configureClosure)
        configuration.getDependencies().add(dependency)
        return dependency
    }

    private fun doAddProvider(configuration: Configuration, dependencyNotation: Provider<*>, configureClosure: Closure<*>): Dependency? {
        if (dependencyNotation is ProviderInternal<*>) {
            val provider = dependencyNotation
            if (provider.getType() != null && ExternalModuleDependencyBundle::class.java.isAssignableFrom(provider.getType())) {
                val bundle: ExternalModuleDependencyBundle = uncheckedCast<ExternalModuleDependencyBundle?>(provider.get())!!
                for (dependency in bundle) {
                    doAddRegularDependency(configuration, dependency, configureClosure)
                }
                return null
            }
        }
        val lazyDependency = dependencyNotation.map<Dependency>(mapDependencyProvider(configuration, configureClosure))
        configuration.getDependencies().addLater(lazyDependency)
        // Return null here because we don't want to prematurely realize the dependency
        return null
    }

    private fun <T> mapDependencyProvider(configuration: Configuration, configureClosure: Closure<*>): Transformer<Dependency, T?> {
        return Transformer { lazyNotation: T? ->
            if (lazyNotation is Configuration) {
                throw InvalidUserDataException("Adding a configuration as a dependency using a provider isn't supported. You should call " + configuration.getName() + ".extendsFrom(" + (lazyNotation as Configuration).getName() + ") instead")
            }
            create(lazyNotation!!, configureClosure)
        }
    }

    override fun project(notation: MutableMap<String, *>): Dependency {
        return dependencyFactory.createProjectDependencyFromMap(notation)
    }

    override fun project(): ProjectDependency {
        return dependencyFactory.createProjectDependency()
    }

    override fun project(projectPath: String): ProjectDependency {
        return dependencyFactory.createProjectDependency(projectPath)
    }

    override fun gradleApi(): Dependency {
        return dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)
    }

    override fun gradleTestKit(): Dependency {
        return dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT)
    }

    override fun localGroovy(): Dependency {
        return dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY)
    }

    override fun getAdditionalMethods(): MethodAccess {
        return dynamicMethods
    }

    override fun constraints(configureAction: Action<in DependencyConstraintHandler>) {
        configureAction.execute(dependencyConstraintHandler)
    }

    override fun getConstraints(): DependencyConstraintHandler {
        return dependencyConstraintHandler
    }

    override fun components(configureAction: Action<in ComponentMetadataHandler>) {
        configureAction.execute(getComponents())
    }

    override fun getComponents(): ComponentMetadataHandler {
        return componentMetadataHandler
    }

    override fun modules(configureAction: Action<in ComponentModuleMetadataHandler>) {
        configureAction.execute(getModules())
    }

    override fun getModules(): ComponentModuleMetadataHandler {
        return componentModuleMetadataHandler
    }

    override fun createArtifactResolutionQuery(): ArtifactResolutionQuery {
        return resolutionQueryFactory.createArtifactResolutionQuery()!!
    }

    override fun attributesSchema(configureAction: Action<in AttributesSchema>): AttributesSchema {
        configureAction.execute(attributesSchema)
        return attributesSchema
    }

    override fun getAttributesSchema(): AttributesSchema {
        return attributesSchema
    }

    private fun configureSchema() {
        attributesSchema.attribute<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE)
    }

    override fun getArtifactTypes(): ArtifactTypeContainer {
        return artifactTypeContainer.artifactTypeContainer
    }

    override fun artifactTypes(configureAction: Action<in ArtifactTypeContainer>) {
        configureAction.execute(getArtifactTypes())
    }

    override fun getDefaultArtifactAttributes(): AttributeContainer {
        return artifactTypeContainer.defaultArtifactAttributes!!
    }

    override fun <T : TransformParameters?> registerTransform(actionType: Class<out TransformAction<T?>>, registrationAction: Action<in TransformSpec<T?>>) {
        transforms.registerTransform<T?>(actionType, registrationAction)
    }

    override fun platform(notation: Any): Dependency {
        val dependency = create(notation)
        if (dependency is ModuleDependency) {
            // Changes here may require changes in DefaultExternalModuleDependencyVariantSpec
            val moduleDependency = dependency
            moduleDependency.endorseStrictVersions()
            platformSupport.addPlatformAttribute<ModuleDependency>(moduleDependency, Category.REGULAR_PLATFORM)
        } else if (dependency is HasConfigurableAttributes<*>) {
            platformSupport.addPlatformAttribute(dependency as HasConfigurableAttributes<*>, Category.REGULAR_PLATFORM)
        }
        return dependency
    }

    override fun platform(notation: Any, configureAction: Action<in Dependency>): Dependency {
        val dep = platform(notation)
        configureAction.execute(dep)
        return dep
    }

    @Suppress("deprecation")
    override fun enforcedPlatform(notation: Any): Dependency {
        val platformDependency = create(notation)
        if (platformDependency is ExternalModuleDependency) {
            // Changes here may require changes in DefaultExternalModuleDependencyVariantSpec
            val externalModuleDependency = platformDependency as AbstractExternalModuleDependency
            val constraint = externalModuleDependency.getVersionConstraint() as MutableVersionConstraint
            constraint.strictly(externalModuleDependency.getVersion()!!)
            platformSupport.addPlatformAttribute<ModuleDependency>(externalModuleDependency, Category.ENFORCED_PLATFORM)
        } else if (platformDependency is HasConfigurableAttributes<*>) {
            platformSupport.addPlatformAttribute(platformDependency as HasConfigurableAttributes<*>, Category.ENFORCED_PLATFORM)
        }
        return platformDependency
    }

    override fun enforcedPlatform(notation: Any, configureAction: Action<in Dependency>): Dependency {
        val dep = enforcedPlatform(notation)
        configureAction.execute(dep)
        return dep
    }

    override fun testFixtures(notation: Any): Dependency {
        val testFixturesDependency = create(notation)
        if (testFixturesDependency is ModuleDependency) {
            // Changes here may require changes in DefaultExternalModuleDependencyVariantSpec
            val moduleDependency = testFixturesDependency
            moduleDependency.capabilities(Action { c: ModuleDependencyCapabilitiesHandler? -> c.requireFeature(TEST_FIXTURES_CAPABILITY_FEATURE_NAME) })
        }
        return testFixturesDependency
    }

    override fun testFixtures(notation: Any, configureAction: Action<in Dependency>): Dependency {
        val testFixturesDependency = testFixtures(notation)
        configureAction.execute(testFixturesDependency)
        return testFixturesDependency
    }

    override fun variantOf(dependencyProvider: Provider<MinimalExternalModuleDependency>, variantSpec: Action<in ExternalModuleDependencyVariantSpec>): Provider<MinimalExternalModuleDependency> {
        return dependencyProvider.map<MinimalExternalModuleDependency>(Transformer { dep: MinimalExternalModuleDependency? ->
            val spec = objects.newInstance<DefaultExternalModuleDependencyVariantSpec>(DefaultExternalModuleDependencyVariantSpec::class.java, dep!!)
            variantSpec.execute(spec)
            DefaultMinimalDependencyVariant(dep, spec.attributesAction, spec.capabilitiesMutator, spec.classifier, spec.artifactType)
        })
    }

    /**
     * Implemented here instead as a default method of DependencyHandler like most of other methods with `Provider<MinimalExternalModuleDependency>` argument
     * since we don't want to expose enforcedPlatform on many places since we might deprecate enforcedPlatform in the future
     *
     * @param dependencyProvider the dependency provider
     */
    override fun enforcedPlatform(dependencyProvider: Provider<MinimalExternalModuleDependency>): Provider<MinimalExternalModuleDependency> {
        return variantOf(dependencyProvider, Action { spec: ExternalModuleDependencyVariantSpec ->
            val defaultSpec = spec as DefaultExternalModuleDependencyVariantSpec
            val versionConstraint = defaultSpec.dep.getVersionConstraint() as MutableVersionConstraint
            versionConstraint.strictly(defaultSpec.dep.getVersion()!!)
            defaultSpec.attributesAction =
                Action { attrs: AttributeContainer? -> attrs!!.attribute<Category>(Category.CATEGORY_ATTRIBUTE, attrs.named<Category>(Category::class.java, Category.ENFORCED_PLATFORM)) }
        })
    }

    private inner class DirectDependencyAdder : DynamicAddDependencyMethods.DependencyAdder<Dependency> {
        override fun add(configuration: Configuration, dependencyNotation: Any, configureAction: Closure<*>?): Dependency {
            return doAdd(configuration, dependencyNotation, configureAction)!!
        }
    }

    class DefaultExternalModuleDependencyVariantSpec @Inject constructor(private val dep: MinimalExternalModuleDependency) : ExternalModuleDependencyVariantSpec {
        private var attributesAction: Action<in AttributeContainer> = null
        private var capabilitiesMutator: Action<ModuleDependencyCapabilitiesHandler> = null
        private var classifier: String? = null
        private var artifactType: String? = null

        override fun platform() {
            this.dep.endorseStrictVersions()
            this.attributesAction =
                Action { attrs: AttributeContainer? -> attrs!!.attribute<Category>(Category.CATEGORY_ATTRIBUTE, attrs.named<Category>(Category::class.java, Category.REGULAR_PLATFORM)) }
        }

        override fun testFixtures() {
            this.capabilitiesMutator = Action { capabilities: ModuleDependencyCapabilitiesHandler? -> capabilities.requireFeature(TEST_FIXTURES_CAPABILITY_FEATURE_NAME) }
        }

        override fun classifier(classifier: String) {
            this.classifier = classifier
        }

        override fun artifactType(artifactType: String) {
            this.artifactType = artifactType
        }
    }
}
