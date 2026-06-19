/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.integtests.tooling.r940

import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.build.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.gradle.*
import org.gradle.tooling.model.idea.*
import org.gradle.tooling.model.kotlin.dsl.*
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import java.io.File
import org.gradle.integtests.tooling.r48.*

import org.gradle.tooling.BuildAction
import java.io.Serializable

// Build action that fetches GradleBuild to initialize, then queries DummyModel and returns its classloader
internal class DummyModelAction : BuildAction<String?>, Serializable {
    public override fun execute(controller: BuildController?): String? {
        // Fetch GradleBuild to force init script evaluation
        controller.fetch(GradleBuild::class.java)

        // Fetch DummyModel
        val result: FetchModelResult<DummyModel?> = controller.fetch(DummyModel::class.java)
        val dummyModel: DummyModel = checkNotNull(result.getModel())
        val unpacked: Any = ProtocolToModelAdapter().unpack(dummyModel)
        val modelBuildersClassLoader: ClassLoader = unpacked.javaClass.getClassLoader()
        return modelBuildersClassLoader.toString()
    }
}
