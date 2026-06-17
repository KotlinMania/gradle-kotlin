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

import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException

abstract class CancellationExceptionTransformer {
    abstract fun transform(e: RuntimeException): RuntimeException?

    companion object {
        private val NO_OP: CancellationExceptionTransformer = object : CancellationExceptionTransformer() {
            public override fun transform(e: RuntimeException): RuntimeException {
                return e
            }
        }

        fun transformerFor(versionDetails: VersionDetails): CancellationExceptionTransformer {
            if (versionDetails.honorsContractOnCancel()) {
                return NO_OP
            }
            return object : CancellationExceptionTransformer() {
                public override fun transform(e: RuntimeException): RuntimeException {
                    var t: Throwable? = e
                    while (t != null) {
                        if ("org.gradle.api.BuildCancelledException" == t.javaClass.getName()
                            || "org.gradle.tooling.BuildCancelledException" == t.javaClass.getName()
                        ) {
                            return InternalBuildCancelledException(e.cause)
                        }
                        t = t.cause
                    }
                    return e
                }
            }
        }
    }
}
