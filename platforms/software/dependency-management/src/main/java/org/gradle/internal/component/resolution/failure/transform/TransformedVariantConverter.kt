/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.resolution.failure.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.transform.TransformStep
import org.gradle.api.internal.artifacts.transform.TransformedVariant
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Consumer

/**
 * This type is responsible for converting from heavyweight [TransformedVariant] instances to
 * lightweight [TransformationChainData] instances.
 *
 *
 * See the [package javadoc][org.gradle.internal.component.resolution.failure.transform] for why.
 */
@ServiceScope(Scope.Project::class)
class TransformedVariantConverter {
    fun convert(transformedVariants: MutableCollection<TransformedVariant>): ImmutableList<TransformationChainData> {
        val builder = ImmutableList.builder<TransformationChainData>()
        transformedVariants.forEach(Consumer { transformedVariant: TransformedVariant? -> builder.add(convert(transformedVariant!!)) })
        return builder.build()
    }

    fun convert(transformedVariant: TransformedVariant): TransformationChainData {
        val visitor = TransformDataRecordingVisitor()
        transformedVariant.getTransformChain().visitTransformSteps(visitor)
        val source = SourceVariantData(transformedVariant.root.asDescribable().getDisplayName(), transformedVariant.root.getAttributes())
        return TransformationChainData(source, visitor.getSteps(), transformedVariant.getAttributes())
    }

    private class TransformDataRecordingVisitor : Action<TransformStep> {
        private val stepsBuilder = ImmutableList.builder<TransformData>()

        override fun execute(transformStep: TransformStep) {
            val transformData = convert(transformStep)
            stepsBuilder.add(transformData)
        }

        fun getSteps(): ImmutableList<TransformData> {
            return stepsBuilder.build()
        }

        fun convert(step: TransformStep): TransformData {
            val transform = step.transform
            return TransformData(transform.implementationClass, transform.getDisplayName(), transform.fromAttributes, transform.toAttributes)
        }
    }
}
