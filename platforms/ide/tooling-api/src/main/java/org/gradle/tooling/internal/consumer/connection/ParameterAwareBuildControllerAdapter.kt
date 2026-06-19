/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import java.io.File

internal open class ParameterAwareBuildControllerAdapter(
    private val buildController: InternalBuildControllerVersion2,
    adapter: ProtocolToModelAdapter,
    modelMapping: ModelMapping,
    gradleVersion: VersionDetails,
    rootDir: File
) : UnparameterizedBuildController(adapter, modelMapping, gradleVersion, rootDir) {
    @Throws(InternalUnsupportedModelException::class)
    override fun getModelResult(target: Any?, modelIdentifier: ModelIdentifier, parameter: Any?): BuildResult<*> {
        return buildController.getModel(target, modelIdentifier, parameter)!!
    }
}
