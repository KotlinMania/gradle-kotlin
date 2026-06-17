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
package org.gradle.tooling.internal.provider.runner

import com.google.common.collect.ImmutableList
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.BuildEventListenerFactory
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.OperationResultPostProcessor
import org.gradle.internal.build.event.OperationResultPostProcessorFactory
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.tooling.events.OperationType
import org.jspecify.annotations.NullMarked
import java.util.function.Supplier

@NullMarked
class ToolingApiBuildEventListenerFactory internal constructor(
    private val ancestryTracker: BuildOperationAncestryTracker,
    private val idFactory: BuildOperationIdFactory,
    private val postProcessorFactories: MutableList<OperationResultPostProcessorFactory>
) : BuildEventListenerFactory {
    override fun createListeners(subscriptions: BuildEventSubscriptions, consumer: BuildEventConsumer): Iterable<Any> {
        if (!subscriptions.isAnyOperationTypeRequested()) {
            return ImmutableList.of<Any>()
        }

        val progressEventConsumer = ProgressEventConsumer(consumer, ancestryTracker)

        val listeners = ImmutableList.builder<Any>()

        if (subscriptions.isRequested(OperationType.TEST) && subscriptions.isRequested(OperationType.TEST_OUTPUT)) {
            listeners.add(ClientForwardingTestOutputOperationListener(progressEventConsumer, idFactory))
        }

        if (subscriptions.isRequested(OperationType.TEST) && subscriptions.isRequested(OperationType.TEST_METADATA)) {
            listeners.add(ClientForwardingTestMetadataOperationListener(progressEventConsumer, idFactory))
        }

        if (subscriptions.isRequested(OperationType.BUILD_PHASE)) {
            listeners.add(BuildPhaseOperationListener(progressEventConsumer, idFactory))
        }

        listeners.add(createClientBuildEventGenerator(subscriptions, consumer, progressEventConsumer))
        return listeners.build()
    }

    private fun createClientBuildEventGenerator(subscriptions: BuildEventSubscriptions, consumer: BuildEventConsumer, progressEventConsumer: ProgressEventConsumer): ClientBuildEventGenerator {
        val buildListener = createBuildOperationListener(subscriptions, progressEventConsumer)

        val operationDependenciesResolver = OperationDependenciesResolver()

        val pluginApplicationTracker = PluginApplicationTracker(ancestryTracker)
        val testTaskTracker = TaskForTestEventTracker(ancestryTracker)
        val projectConfigurationTracker = ProjectConfigurationTracker(ancestryTracker, pluginApplicationTracker)

        val transformOperationMapper = TransformOperationMapper(operationDependenciesResolver)
        operationDependenciesResolver.addLookup(transformOperationMapper)

        val postProcessors = createPostProcessors(subscriptions, consumer)

        val taskOperationMapper = TaskOperationMapper(postProcessors, operationDependenciesResolver)
        operationDependenciesResolver.addLookup(taskOperationMapper)

        val mappers: MutableList<BuildOperationMapper<*, *>> = ImmutableList.of<BuildOperationMapper<*, *>>(
            FileDownloadOperationMapper(),
            TestOperationMapper(testTaskTracker),
            ProjectConfigurationOperationMapper(projectConfigurationTracker),
            taskOperationMapper,
            transformOperationMapper,
            WorkItemOperationMapper()
        )
        return ClientBuildEventGenerator(progressEventConsumer, subscriptions, mappers, buildListener)
    }

    private fun createPostProcessors(subscriptions: BuildEventSubscriptions, consumer: BuildEventConsumer): MutableList<OperationResultPostProcessor> {
        return postProcessorFactories.stream()
            .map<MutableList<OperationResultPostProcessor>> { factory: OperationResultPostProcessorFactory? -> factory!!.createProcessors(subscriptions, consumer) }
            .flatMap<OperationResultPostProcessor> { obj: MutableList<OperationResultPostProcessor?>? -> obj!!.stream() }
            .collect(ImmutableList.toImmutableList<OperationResultPostProcessor>())
    }

    private fun createBuildOperationListener(subscriptions: BuildEventSubscriptions, progressEventConsumer: ProgressEventConsumer): BuildOperationListener {
        // TODO (donat) think of a better name for this class
        return ClientForwardingBuildOperationListener(progressEventConsumer, subscriptions, Supplier { OperationIdentifier(idFactory.nextId()) })
    }
}
