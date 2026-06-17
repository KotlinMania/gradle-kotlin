/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner

import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer

@ServiceScope(Scope.BuildTree::class)
class BuildControllerFactory(
    private val workerThreadRegistry: WorkerThreadRegistry,
    private val buildCancellationToken: BuildCancellationToken,
    private val buildEventConsumer: BuildEventConsumer,
    private val sideEffectExecutor: BuildTreeModelSideEffectExecutor,
    private val payloadSerializer: PayloadSerializer
) {
    fun controllerFor(controller: BuildTreeModelController): DefaultBuildController {
        return DefaultBuildController(
            controller,
            workerThreadRegistry,
            buildCancellationToken,
            buildEventConsumer,
            sideEffectExecutor,
            payloadSerializer
        )
    }
}
