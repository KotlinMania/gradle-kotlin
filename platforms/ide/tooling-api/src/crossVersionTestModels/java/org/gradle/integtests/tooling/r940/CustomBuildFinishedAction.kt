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

import org.gradle.tooling.BuildAction
import java.util.function.Supplier

internal class CustomBuildFinishedAction(val callType: CallType?) : BuildAction<String?> {
    enum class CallType {
        GET_MODEL,
        FIND_MODEL,
        FETCH
    }

    public override fun execute(controller: BuildController): String? {
        println("Running CustomBuildFinishedAction")
        if (callType == CallType.GET_MODEL) {
            return tryGet(Supplier { controller.getModel(CustomBuildFinishedModel::class.java).toString() })
        } else if (callType == CallType.FIND_MODEL) {
            return tryGet(Supplier { controller.findModel(CustomBuildFinishedModel::class.java).toString() })
        } else if (callType == CallType.FETCH) {
            val result: FetchModelResult<CustomBuildFinishedModel?> = controller.fetch(CustomBuildFinishedModel::class.java)
            if (result.getModel() != null) {
                assert(result.getFailures().isEmpty())
                return result.getModel().getValue()
            } else {
                assert(!result.getFailures().isEmpty())
                return FAILURE_RESULT
            }
        } else {
            throw UnsupportedOperationException("Unknown callType: \$callType")
        }
    }

    companion object {
        const val FAILURE_RESULT: String = "<failure>"

        private fun tryGet(tryGet: Supplier<String?>): String? {
            try {
                return tryGet.get()
            } catch (ignored: Exception) {
                return FAILURE_RESULT
            }
        }
    }
}
