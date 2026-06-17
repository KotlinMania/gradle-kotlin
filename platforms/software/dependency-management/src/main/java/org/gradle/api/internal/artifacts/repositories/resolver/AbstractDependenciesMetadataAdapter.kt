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
package org.gradle.api.internal.artifacts.repositories.resolver

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.artifacts.DependenciesMetadata
import org.gradle.api.artifacts.DependencyMetadata
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.GradleDependencyMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

abstract class AbstractDependenciesMetadataAdapter<T : DependencyMetadata<T?>?, E : T?>(
    private val attributesFactory: AttributesFactory,
    private val instantiator: Instantiator,
    private val dependencyNotationParser: NotationParser<Any, T?>
) : ArrayList<T?>(), DependenciesMetadata<T?> {
    protected abstract fun adapterImplementationType(): Class<E?>?

    protected abstract fun getAdapterMetadata(adapter: E?): ModuleDependencyMetadata?

    protected abstract val isConstraint: Boolean

    protected abstract fun isEndorsingStrictVersions(details: T?): Boolean

    override fun add(dependencyNotation: String) {
        doAdd(dependencyNotation, null)
    }

    @Deprecated("")
    override fun add(dependencyNotation: MutableMap<String, String>) {
        doAdd(dependencyNotation, null)
    }

    override fun add(dependencyNotation: String, configureAction: Action<in T?>) {
        doAdd(dependencyNotation, configureAction)
    }

    @Deprecated("")
    override fun add(dependencyNotation: MutableMap<String, String>, configureAction: Action<in T?>) {
        doAdd(dependencyNotation, configureAction)
    }

    private fun doAdd(dependencyNotation: Any, configureAction: Action<in T?>?) {
        val dependencyMetadata = dependencyNotationParser.parseNotation(dependencyNotation)
        if (dependencyMetadata is AbstractDependencyImpl<*>) {
            // This is not super nice, but dependencies are created through reflection, for decoration
            // and assume a constructor with 3 arguments (Group, Name, Version) which is suitable for
            // most cases. We could create an empty attribute set directly in the AbstractDependencyImpl,
            // but then it wouldn't be mutable. Therefore we proceed with "late injection" of the attributes
            (dependencyMetadata as AbstractDependencyImpl<*>).setAttributes(attributesFactory.mutable())
        }

        val adapted: T? = adapt(dependencyMetadata)
        if (configureAction != null) {
            configureAction.execute(adapted)
        }
        add(adapted)
    }

    val metadatas: ImmutableList<ModuleDependencyMetadata>
        get() = this.stream().map<E?> { details: T? -> this.maybeAdapt(details) }
            .map<ModuleDependencyMetadata> { adapter: E? -> this.getAdapterMetadata(adapter) }
            .collect(ImmutableList.toImmutableList<ModuleDependencyMetadata>())

    private fun maybeAdapt(details: T?): E? {
        if (adapterImplementationType()!!.isInstance(details)) {
            return adapterImplementationType()!!.cast(details)
        }

        return adapt(details)
    }

    private fun adapt(details: T?): E? {
        val selector =
            DefaultModuleComponentSelector.newSelector(details!!.getModule(), DefaultImmutableVersionConstraint.of(details.getVersionConstraint()), details.getAttributes(), ImmutableSet.of<E?>())
        val dependencyMetadata = GradleDependencyMetadata(
            selector, ImmutableList.of<ExcludeMetadata?>(),
            this.isConstraint, isEndorsingStrictVersions(details), details.getReason(), false, null
        )
        return instantiator.newInstance<E?>(adapterImplementationType(), attributesFactory, dependencyMetadata)
    }
}
