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
package org.gradle.api.internal.tasks

import org.gradle.api.Namer
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.reflect.Instantiator
import javax.inject.Inject

class DefaultSourceSetContainer @Inject constructor(
    private val fileResolver: FileResolver?,
    private val taskDependencyFactory: TaskDependencyFactory?,
    private val fileCollectionFactory: FileCollectionFactory?,
    private val instantiator: Instantiator,
    private val objectFactory: ObjectFactory?,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : AbstractValidatingNamedDomainObjectContainer<SourceSet?>(
    SourceSet::class.java,
    instantiator, object : Namer<SourceSet?> {
        override fun determineName(ss: SourceSet): String? {
            return ss.getName()
        }
    }, collectionCallbackActionDecorator
), SourceSetContainer {
    override fun doCreate(name: String): SourceSet {
        val sourceSet = instantiator.newInstance<DefaultSourceSet>(DefaultSourceSet::class.java, name, objectFactory)
        sourceSet.setClasses(
            instantiator.newInstance<DefaultSourceSetOutput?>(
                DefaultSourceSetOutput::class.java,
                sourceSet.getDisplayName(),
                taskDependencyFactory,
                fileResolver,
                fileCollectionFactory
            )
        )
        return sourceSet
    }

    override fun getPublicType(): TypeOf<*> {
        return TypeOf.typeOf<SourceSetContainer?>(SourceSetContainer::class.java)
    }
}
