/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.management

import com.google.common.collect.Interners
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.reflect.Instantiator
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.inject.Inject

class DefaultVersionCatalogBuilderContainer @Inject constructor(
    instantiator: Instantiator,
    callbackActionDecorator: CollectionCallbackActionDecorator,
    private val objects: ObjectFactory,
    private val context: UserCodeApplicationContext,
    private val dependencyResolutionServices: Supplier<DependencyResolutionServices?>?
) : AbstractNamedDomainObjectContainer<VersionCatalogBuilder>(
    VersionCatalogBuilder::class.java, instantiator, callbackActionDecorator
), MutableVersionCatalogContainer {
    private val strings = Interners.newStrongInterner<String?>()
    private val versions = Interners.newStrongInterner<ImmutableVersionConstraint?>()

    @Throws(InvalidUserDataException::class)
    override fun create(name: String, configureAction: Action<in VersionCatalogBuilder?>): VersionCatalogBuilder {
        validateName(name)
        return super.create(name, Action { model: VersionCatalogBuilder ->
            val current = context.current()
            val builder = model as DefaultVersionCatalogBuilder
            builder.withContext(if (current == null) "Settings" else current.getSource().getDisplayName().getDisplayName(), Runnable { configureAction.execute(model) })
        })
    }

    override fun doCreate(name: String): VersionCatalogBuilder {
        return objects.newInstance<DefaultVersionCatalogBuilder>(DefaultVersionCatalogBuilder::class.java, name, strings, versions, objects, dependencyResolutionServices!!)
    }

    override fun getPublicType(): TypeOf<*> {
        return TypeOf.typeOf<MutableVersionCatalogContainer?>(MutableVersionCatalogContainer::class.java)
    }

    companion object {
        private const val VALID_EXTENSION_NAME = "[a-z]([a-zA-Z0-9])+"
        private val VALID_EXTENSION_PATTERN: Pattern = Pattern.compile(VALID_EXTENSION_NAME)

        private fun validateName(name: String) {
            if (!VALID_EXTENSION_PATTERN.matcher(name).matches()) {
                throw InvalidUserDataException("Invalid model name '" + name + "': it must match the following regular expression: " + VALID_EXTENSION_NAME)
            }
        }
    }
}
