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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.Action
import org.gradle.internal.MutableActionSet
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain
import java.io.File

abstract class ExtendableToolChain<T : NativePlatformToolChain?> protected constructor(
    private val name: String?,
    @JvmField protected val buildOperationExecutor: BuildOperationExecutor?,
    @JvmField protected val operatingSystem: OperatingSystem,
    private val fileResolver: PathToFileResolver
) : NativeToolChainInternal {
    @JvmField
    protected val configureActions: MutableActionSet<T?> = MutableActionSet<T?>()

    override fun getName(): String? {
        return name
    }

    protected abstract val typeName: String?

    val displayName: String?
        get() = "Tool chain '" + getName() + "' (" + this.typeName + ")"

    override fun toString(): String {
        return this.displayName!!
    }

    val outputType: String?
        get() = getName() + "-" + operatingSystem.name

    fun eachPlatform(action: Action<in T?>) {
        configureActions.add(action)
    }

    protected fun resolve(path: Any?): File? {
        return fileResolver.resolve(path)
    }

    override fun assertSupported() {
        // Supported, nothing to do.
    }
}
