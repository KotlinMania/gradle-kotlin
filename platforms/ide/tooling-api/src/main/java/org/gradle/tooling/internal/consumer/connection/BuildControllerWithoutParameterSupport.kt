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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.api.Action
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalBuildController
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.model.Model
import java.io.File

@Suppress("deprecation")
class BuildControllerWithoutParameterSupport(
    private val buildController: InternalBuildController,
    adapter: ProtocolToModelAdapter,
    modelMapping: ModelMapping,
    rootDir: File,
    gradleVersion: VersionDetails
) : UnparameterizedBuildController(adapter, modelMapping, gradleVersion, rootDir) {
    @Throws(UnsupportedVersionException::class)
    override fun <T, P> getModel(target: Model, modelType: Class<T?>, parameterType: Class<P?>, parameterInitializer: Action<in P?>): T? {
        if (parameterType != null) {
            throw UnsupportedVersionException(String.format("Gradle version %s does not support parameterized tooling models.", gradleVersion.getVersion()))
        }
        return super.getModel<T?, P?>(target, modelType, parameterType, parameterInitializer)
    }

    @Throws(InternalUnsupportedModelException::class)
    override fun getModel(target: Any?, modelIdentifier: ModelIdentifier, parameter: Any?): BuildResult<*> {
        return buildController.getModel(target, modelIdentifier)
    }
}
