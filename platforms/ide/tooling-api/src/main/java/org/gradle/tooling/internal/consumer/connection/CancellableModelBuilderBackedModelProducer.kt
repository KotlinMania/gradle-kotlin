/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.ViewBuilder.build
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalCancellableConnection
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.model.internal.Exceptions

class CancellableModelBuilderBackedModelProducer(
    protected val adapter: ProtocolToModelAdapter,
    protected val versionDetails: VersionDetails,
    protected val modelMapping: ModelMapping,
    private val builder: InternalCancellableConnection,
    protected val exceptionTransformer: CancellationExceptionTransformer
) : HasCompatibilityMapping(
    versionDetails
), ModelProducer {
    override fun <T> produceModel(type: Class<T?>, operationParameters: ConsumerOperationParameters): T? {
        if (!versionDetails.maySupportModel(type)) {
            throw Exceptions.unsupportedModel(type, versionDetails.getVersion())
        }
        val modelIdentifier = modelMapping.getModelIdentifierFromModelType(type)
        val result: BuildResult<*>
        try {
            result = builder.getModel(modelIdentifier, BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters)
        } catch (e: InternalUnsupportedModelException) {
            throw Exceptions.unknownModel(type, e)
        } catch (e: RuntimeException) {
            throw exceptionTransformer.transform(e)
        }
        return applyCompatibilityMapping<T?>(adapter.builder<T?>(type), operationParameters).build(result.model)
    }
}
