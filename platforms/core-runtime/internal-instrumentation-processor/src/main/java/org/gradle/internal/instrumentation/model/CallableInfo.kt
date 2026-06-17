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

interface CallableInfo {
    @JvmField
    val kind: CallableKindInfo?

    @JvmField
    val owner: CallableOwnerInfo?

    @JvmField
    val callableName: String?

    @JvmField
    val returnType: CallableReturnTypeInfo?

    @JvmField
    val parameters: MutableList<ParameterInfo?>?

    /**
     * Returns true if the interceptor method has a parameter annotated with [KotlinDefaultMask].
     *
     * @return true if the method has a default mask parameter
     */
    fun hasKotlinDefaultMaskParam(): Boolean {
        return this.parameters!!.stream().anyMatch { it: ParameterInfo? -> it!!.getKind() == ParameterKindInfo.KOTLIN_DEFAULT_MASK }
    }

    /**
     * Returns true if the interceptor method has a parameter annotated with [CallerClassName].
     *
     * @return true if the method has a caller class name parameter
     */
    fun hasCallerClassNameParam(): Boolean {
        return this.parameters!!.stream().anyMatch { it: ParameterInfo? -> it!!.getKind() == ParameterKindInfo.CALLER_CLASS_NAME }
    }

    /**
     * Returns true if the interceptor method has a parameter annotated with [InjectVisitorContext].
     *
     * @return true if the method has a visitor context parameter
     */
    fun hasInjectVisitorContextParam(): Boolean {
        return this.parameters!!.stream().anyMatch { it: ParameterInfo? -> it!!.getKind() == ParameterKindInfo.INJECT_VISITOR_CONTEXT }
    }
}
