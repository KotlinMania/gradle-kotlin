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
package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.BuildAction
import java.util.stream.Collectors

internal open class FetchCustomModelAction : BuildAction<Result<String?>?> {
    public override fun execute(controller: BuildController): Result<String?> {
        val result: FetchModelResult<CustomModel?> = fetch(controller)
        val failures: MutableList<String?>? = result.getFailures().stream()
            .map(Failure::getMessage)
            .collect(Collectors.toList())
        val causes: MutableList<String?>? = result.getFailures().stream()
            .flatMap({ f -> f.getCauses().stream() })
            .map(Failure::getMessage)
            .collect(Collectors.toList())
        val model: CustomModel? = result.getModel()
        return Result<String?>(if (model != null) model.value else null, failures, causes)
    }

    protected open fun fetch(controller: BuildController): FetchModelResult<CustomModel?> {
        return controller.fetch(CustomModel::class.java, null, null)
    }

    companion object {
        fun withFetchModelCall(): FetchCustomModelAction {
            return object : FetchCustomModelAction() {
                public override fun fetch(controller: BuildController): FetchModelResult<CustomModel?> {
                    return controller.fetch(CustomModel::class.java)
                }
            }
        }

        fun withFetchTargetModelCall(): FetchCustomModelAction {
            return object : FetchCustomModelAction() {
                public override fun fetch(controller: BuildController): FetchModelResult<CustomModel?> {
                    return controller.fetch(CustomModel::class.java)
                }
            }
        }

        fun withFetchModelParametersCall(): FetchCustomModelAction {
            return object : FetchCustomModelAction() {
                public override fun fetch(controller: BuildController): FetchModelResult<CustomModel?> {
                    return controller.fetch(CustomModel::class.java, null, null)
                }
            }
        }
    }
}
