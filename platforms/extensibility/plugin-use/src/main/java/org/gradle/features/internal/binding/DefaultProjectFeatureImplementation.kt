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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.TargetTypeInformation
import java.util.Objects

/**
 * Represents a resolved project type implementation.  Used by declarative DSL to understand which model types should be exposed for
 * which project types.
 */
class DefaultProjectFeatureImplementation<OwnDefinition : Definition<OwnBuildModel?>?, OwnBuildModel : BuildModel?>(
    private val featureName: String,
    private val definitionPublicType: Class<OwnDefinition?>,
    private val definitionImplementationType: Class<out OwnDefinition>,
    private val definitionSafety: ProjectFeatureBindingDeclaration.Safety,
    private val applyActionSafety: ProjectFeatureBindingDeclaration.Safety,
    private val targetDefinitionType: TargetTypeInformation<*>,
    private val buildModelType: Class<OwnBuildModel?>,
    private val buildModelImplementationType: Class<out OwnBuildModel>,
    private val nestedBuildModelTypesToImplementationTypes: MutableMap<Class<*>, Class<*>>,
    private val pluginClass: Class<out Plugin<Project>>,
    private val registeringPluginClass: Class<out Plugin<Settings>>,
    private val registeringPluginId: String?,
    private val applyActionFactory: ProjectFeatureApplyActionFactory<OwnDefinition?, OwnBuildModel?, *>
) : ProjectFeatureImplementation<OwnDefinition?, OwnBuildModel?> {
    private val defaults: MutableList<ModelDefault<*>> = ArrayList<ModelDefault<*>>()

    override fun getFeatureName(): String {
        return featureName
    }

    override fun getDefinitionPublicType(): Class<OwnDefinition?> {
        return definitionPublicType
    }

    override fun getDefinitionImplementationType(): Class<out OwnDefinition> {
        return definitionImplementationType
    }

    override fun getDefinitionSafety(): ProjectFeatureBindingDeclaration.Safety {
        return definitionSafety
    }

    override fun getApplyActionSafety(): ProjectFeatureBindingDeclaration.Safety {
        return applyActionSafety
    }

    override fun getBuildModelType(): Class<OwnBuildModel?> {
        return buildModelType
    }

    override fun getBuildModelImplementationType(): Class<out OwnBuildModel> {
        return buildModelImplementationType
    }

    override fun getNestedBuildModelTypes(): MutableMap<Class<*>, Class<*>> {
        return nestedBuildModelTypesToImplementationTypes
    }

    override fun getTargetDefinitionType(): TargetTypeInformation<*> {
        return targetDefinitionType
    }

    override fun getPluginClass(): Class<out Plugin<Project>> {
        return pluginClass
    }

    override fun getRegisteringPluginClass(): Class<out Plugin<Settings>> {
        return registeringPluginClass
    }

    override fun getRegisteringPluginId(): String? {
        return registeringPluginId
    }

    override fun getApplyActionFactory(): ProjectFeatureApplyActionFactory<OwnDefinition?, OwnBuildModel?, *> {
        return applyActionFactory
    }

    override fun addModelDefault(modelDefault: ModelDefault<*>) {
        defaults.add(modelDefault)
    }

    override fun <M : ModelDefault.Visitor<*>?> visitModelDefaults(type: Class<out ModelDefault<M?>>, visitor: M?) {
        defaults.stream()
            .filter { obj: ModelDefault<*>? -> type.isInstance(obj) }
            .map { obj: Any? -> type.cast(obj) }
            .forEach { modelDefault: ModelDefault<M?> -> modelDefault.visit(visitor) }
    }

    override fun equals(o: Any): Boolean {
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultProjectFeatureImplementation<*, *>
        return featureName == that.featureName
                && definitionPublicType == that.definitionPublicType
                && definitionImplementationType == that.definitionImplementationType
                && definitionSafety == that.definitionSafety && applyActionSafety == that.applyActionSafety && targetDefinitionType == that.targetDefinitionType
                && buildModelType == that.buildModelType
                && buildModelImplementationType == that.buildModelImplementationType
                && pluginClass == that.pluginClass
                && registeringPluginClass == that.registeringPluginClass
                && defaults == that.defaults
                && registeringPluginId == that.registeringPluginId
                && applyActionFactory == that.applyActionFactory
    }

    override fun hashCode(): Int {
        return Objects.hash(
            featureName,
            definitionPublicType,
            definitionImplementationType,
            definitionSafety,
            applyActionSafety,
            targetDefinitionType,
            buildModelType,
            buildModelImplementationType,
            pluginClass,
            registeringPluginClass,
            defaults,
            registeringPluginId,
            applyActionFactory
        )
    }
}
