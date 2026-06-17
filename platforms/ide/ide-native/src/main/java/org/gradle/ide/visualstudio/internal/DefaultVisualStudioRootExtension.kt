/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.ide.visualstudio.internal

import org.gradle.api.Action
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.ide.visualstudio.VisualStudioRootExtension
import org.gradle.ide.visualstudio.VisualStudioSolution
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.internal.IdeArtifactRegistry

class DefaultVisualStudioRootExtension(
    projectName: String,
    instantiator: Instantiator?,
    objectFactory: ObjectFactory,
    fileResolver: FileResolver?,
    ideArtifactRegistry: IdeArtifactRegistry?,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator?,
    providerFactory: ProviderFactory?
) : DefaultVisualStudioExtension(instantiator, fileResolver, ideArtifactRegistry, collectionCallbackActionDecorator, objectFactory, providerFactory), VisualStudioRootExtension,
    VisualStudioExtensionInternal {
    private val solution: VisualStudioSolution

    init {
        this.solution = objectFactory.newInstance<DefaultVisualStudioSolution>(DefaultVisualStudioSolution::class.java, projectName)
    }

    override fun getSolution(): VisualStudioSolution {
        return solution
    }

    override fun solution(configAction: Action<in VisualStudioSolution?>) {
        configAction.execute(solution)
    }
}
