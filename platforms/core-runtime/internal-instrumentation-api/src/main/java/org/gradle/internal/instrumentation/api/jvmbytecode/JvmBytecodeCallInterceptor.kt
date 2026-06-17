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
package org.gradle.internal.instrumentation.api.jvmbytecode

import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter
import org.gradle.internal.instrumentation.api.types.FilterableBytecodeInterceptor
import org.gradle.internal.instrumentation.api.types.FilterableBytecodeInterceptorFactory
import org.gradle.model.internal.asm.MethodVisitorScope
import org.objectweb.asm.tree.MethodNode
import java.util.function.Supplier

interface JvmBytecodeCallInterceptor : FilterableBytecodeInterceptor {
    fun visitMethodInsn(
        mv: MethodVisitorScope,
        className: String,
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
        readMethodNode: Supplier<MethodNode>
    ): Boolean

    fun findBridgeMethodBuilder(className: String, tag: Int, owner: String, name: String, descriptor: String): BridgeMethodBuilder?

    interface Factory : FilterableBytecodeInterceptorFactory {
        fun create(metadata: InstrumentationMetadata, interceptorFilter: BytecodeInterceptorFilter): JvmBytecodeCallInterceptor?
    }
}
