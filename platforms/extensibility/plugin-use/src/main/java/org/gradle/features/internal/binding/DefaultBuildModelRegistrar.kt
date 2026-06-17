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

import org.gradle.api.model.ObjectFactory
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.inspection.DefaultTypeParameterInspection
import org.gradle.internal.inspection.TypeParameterInspection

class DefaultBuildModelRegistrar(
    private val objectFactory: ObjectFactory,
    private val projectFeatureApplicator: ProjectFeatureApplicator,
    private val projectFeatureDeclarations: ProjectFeatureDeclarations
) : BuildModelRegistrarInternal {
    override fun <T : Definition<V?>?, V : BuildModel?> registerBuildModel(definition: T?, implementationType: Class<out V>): V? {
        val maybeContext = ProjectFeatureSupportInternal.tryGetContext(definition!!)
        if (maybeContext != null) {
            return uncheckedCast<V?>(maybeContext.getBuildModel())
        }

        val buildModel: V? = ProjectFeatureSupportInternal.createBuildModelInstance(objectFactory, implementationType)
        ProjectFeatureSupportInternal.attachDefinitionContext<V?>(definition, buildModel, projectFeatureApplicator, projectFeatureDeclarations, objectFactory)

        return buildModel
    }

    override fun <T : Definition<V?>?, V : BuildModel?> registerBuildModel(definition: T?): V? {
        val inspection: TypeParameterInspection<Definition<*>, BuildModel> =
            DefaultTypeParameterInspection<Definition<*>, BuildModel>(Definition::class.java, BuildModel::class.java, BuildModel.None::class.java)
        val modelType: Class<V?> = inspection.parameterTypeFor(definition!!.javaClass)

        return registerBuildModel<T?, V?>(definition, modelType)
    }

    override fun <T : Definition<V?>?, V : BuildModel?> registerBuildModel(definition: T?, nestedBuildModelTypesToImplementationTypes: MutableMap<Class<*>, Class<*>>): V? {
        val inspection: TypeParameterInspection<Definition<*>, BuildModel> =
            DefaultTypeParameterInspection<Definition<*>, BuildModel>(Definition::class.java, BuildModel::class.java, BuildModel.None::class.java)
        val modelType: Class<V?> = inspection.parameterTypeFor(definition!!.javaClass)

        if (nestedBuildModelTypesToImplementationTypes.containsKey(modelType)) {
            val buildModelImplementationType = uncheckedCast<Class<out V>?>(nestedBuildModelTypesToImplementationTypes.get(modelType))
            return registerBuildModel<T?, V?>(definition, buildModelImplementationType)
        } else {
            return registerBuildModel<T?, V?>(definition, modelType)
        }
    }
}
