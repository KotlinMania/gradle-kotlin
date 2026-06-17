/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.asm

import org.jspecify.annotations.NullMarked
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import java.util.AbstractList
import java.util.Arrays

/**
 * Unifying type between `invokedynamic` and `CONSTANT_Dynamic` bootstrap methods.
 */
@NullMarked
class BootstrapMethod private constructor(val handle: Handle, val arguments: MutableList<Any>) {
    private class ConstantDynamicBootstrapArguments(private val constantDynamic: ConstantDynamic) : AbstractList<Any>() {
        override fun get(index: Int): Any {
            return constantDynamic.getBootstrapMethodArgument(index)
        }

        override fun size(): Int {
            return constantDynamic.getBootstrapMethodArgumentCount()
        }
    }

    companion object {
        fun fromIndy(bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any): BootstrapMethod {
            return BootstrapMethod(bootstrapMethodHandle, Arrays.asList<Any>(*bootstrapMethodArguments))
        }

        fun fromConstantDynamic(constantDynamic: ConstantDynamic): BootstrapMethod {
            return BootstrapMethod(constantDynamic.getBootstrapMethod(), ConstantDynamicBootstrapArguments(constantDynamic))
        }
    }
}
