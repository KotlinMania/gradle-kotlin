/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.model

import org.gradle.internal.instrumentation.api.annotations.ParameterKind

enum class ParameterKindInfo {
    RECEIVER, METHOD_PARAMETER, VARARG_METHOD_PARAMETER, CALLER_CLASS_NAME, KOTLIN_DEFAULT_MASK, INJECT_VISITOR_CONTEXT;

    val isSourceParameter: Boolean
        get() = this == ParameterKindInfo.METHOD_PARAMETER || this == ParameterKindInfo.VARARG_METHOD_PARAMETER

    companion object {
        @JvmStatic
        fun fromAnnotation(annotation: Annotation?): ParameterKindInfo {
            if (annotation is ParameterKind.Receiver) {
                return ParameterKindInfo.RECEIVER
            }
            if (annotation is ParameterKind.CallerClassName) {
                return ParameterKindInfo.CALLER_CLASS_NAME
            }
            if (annotation is ParameterKind.KotlinDefaultMask) {
                return ParameterKindInfo.KOTLIN_DEFAULT_MASK
            }
            if (annotation is ParameterKind.VarargParameter) {
                return ParameterKindInfo.VARARG_METHOD_PARAMETER
            }
            if (annotation is ParameterKind.InjectVisitorContext) {
                return ParameterKindInfo.INJECT_VISITOR_CONTEXT
            }
            throw IllegalArgumentException("Unexpected annotation " + annotation)
        }
    }
}
