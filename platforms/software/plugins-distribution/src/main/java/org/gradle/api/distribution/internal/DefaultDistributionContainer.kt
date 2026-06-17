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
package org.gradle.api.distribution.internal

import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.reflect.Instantiator
import javax.inject.Inject

/**
 * Default implementation for [DistributionContainer]
 */
class DefaultDistributionContainer @Inject constructor(
    type: Class<Distribution?>,
    instantiator: Instantiator,
    private val objectFactory: ObjectFactory,
    private val fileOperations: FileOperations,
    callbackDecorator: CollectionCallbackActionDecorator
) : AbstractNamedDomainObjectContainer<Distribution?>(type, instantiator, callbackDecorator), DistributionContainer {
    override fun doCreate(name: String): Distribution {
        return objectFactory.newInstance<DefaultDistribution>(DefaultDistribution::class.java, name, fileOperations.copySpec())
    }
}
