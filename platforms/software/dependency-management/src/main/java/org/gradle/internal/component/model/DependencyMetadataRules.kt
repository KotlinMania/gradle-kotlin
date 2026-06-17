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
package org.gradle.internal.component.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.DependencyConstraintMetadata
import org.gradle.api.artifacts.DependencyConstraintsMetadata
import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataAdapter
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintsMetadataAdapter
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependenciesMetadataAdapter
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataAdapter
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.specs.Spec
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.VariantMetadataRules
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.util.internal.CollectionUtils
import java.util.function.Consumer


/**
 * A set of rules provided by the build script author
 * (as [<] or [<])
 * that are applied on the dependencies defined in variant/configuration metadata. The rules are applied
 * in the [.execute] method when the dependencies of a variant are needed during dependency resolution.
 */
class DependencyMetadataRules(
    private val instantiator: Instantiator,
    private val dependencyNotationParser: NotationParser<Any, DirectDependencyMetadata>,
    private val dependencyConstraintNotationParser: NotationParser<Any, DependencyConstraintMetadata>,
    private val attributesFactory: AttributesFactory
) {
    private val dependencyActions: MutableList<VariantMetadataRules.VariantAction<in DirectDependenciesMetadata>> = ArrayList<VariantMetadataRules.VariantAction<in DirectDependenciesMetadata>>()
    private val dependencyConstraintActions: MutableList<VariantMetadataRules.VariantAction<in DependencyConstraintsMetadata>> =
        ArrayList<VariantMetadataRules.VariantAction<in DependencyConstraintsMetadata>>()

    fun addDependencyAction(action: VariantMetadataRules.VariantAction<in DirectDependenciesMetadata>) {
        dependencyActions.add(action)
    }

    fun addDependencyConstraintAction(action: VariantMetadataRules.VariantAction<in DependencyConstraintsMetadata>) {
        dependencyConstraintActions.add(action)
    }

    fun <T : ModuleDependencyMetadata?> execute(variant: VariantResolveMetadata, dependencies: MutableList<T?>): MutableList<out ModuleDependencyMetadata> {
        val calculatedDependencies = ImmutableList.Builder<ModuleDependencyMetadata>()
        calculatedDependencies.addAll(executeDependencyRules<T?>(variant, dependencies))
        calculatedDependencies.addAll(executeDependencyConstraintRules<T?>(variant, dependencies))
        return calculatedDependencies.build()
    }

    private fun <T : ModuleDependencyMetadata?> executeDependencyRules(variant: VariantResolveMetadata, dependencies: MutableList<T?>): MutableList<out ModuleDependencyMetadata> {
        if (dependencyActions.isEmpty()) {
            return CollectionUtils.filter<T?>(dependencies, DEPENDENCY_FILTER)
        }

        val adapter = instantiator.newInstance<DirectDependenciesMetadataAdapter>(
            DirectDependenciesMetadataAdapter::class.java, attributesFactory, instantiator, dependencyNotationParser
        )
        CollectionUtils.filter<T?>(dependencies, DEPENDENCY_FILTER)
            .forEach(Consumer { dep: T? -> adapter.add(instantiator.newInstance<DirectDependencyMetadataAdapter>(DirectDependencyMetadataAdapter::class.java, attributesFactory, dep)) })

        dependencyActions.forEach(Consumer { action: VariantMetadataRules.VariantAction<in DirectDependenciesMetadata?>? -> action!!.maybeExecute(variant, adapter) })

        return adapter.getMetadatas()
    }

    private fun <T : ModuleDependencyMetadata?> executeDependencyConstraintRules(variant: VariantResolveMetadata, dependencies: MutableList<T?>): MutableList<out ModuleDependencyMetadata> {
        if (dependencyConstraintActions.isEmpty()) {
            return CollectionUtils.filter<T?>(dependencies, DEPENDENCY_CONSTRAINT_FILTER)
        }

        val adapter = instantiator.newInstance<DependencyConstraintsMetadataAdapter>(
            DependencyConstraintsMetadataAdapter::class.java, attributesFactory, instantiator, dependencyConstraintNotationParser
        )

        CollectionUtils.filter<T?>(dependencies, DEPENDENCY_CONSTRAINT_FILTER).forEach(Consumer { dep: T? ->
            adapter.add(
                instantiator.newInstance<DependencyConstraintMetadataAdapter>(
                    DependencyConstraintMetadataAdapter::class.java, attributesFactory, dep
                )
            )
        })

        dependencyConstraintActions.forEach(Consumer { action: VariantMetadataRules.VariantAction<in DependencyConstraintsMetadata?>? -> action!!.maybeExecute(variant, adapter) })

        return adapter.getMetadatas()
    }

    companion object {
        private val DEPENDENCY_FILTER: Spec<ModuleDependencyMetadata?> = org.gradle.api.specs.Spec { dep: ModuleDependencyMetadata? -> !dep!!.isConstraint() }
        private val DEPENDENCY_CONSTRAINT_FILTER: Spec<ModuleDependencyMetadata?> = org.gradle.api.specs.Spec { obj: ModuleDependencyMetadata? -> obj!!.isConstraint() }
    }
}
