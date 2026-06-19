/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.events.task.internal

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.internal.DefaultOperationDescriptor
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor
import org.gradle.tooling.model.internal.Exceptions

/**
 * Implementation of the `TaskOperationDescriptor` interface.
 */
class DefaultTaskOperationDescriptor private constructor(
    descriptor: InternalTaskDescriptor,
    parent: OperationDescriptor?,
    override val taskPath: String?,
    private val dependenciesSupplier: Supplier<MutableSet<OperationDescriptor?>?>,
    private val originPluginSupplier: Supplier<PluginIdentifier?>
) : DefaultOperationDescriptor(descriptor, parent), TaskOperationDescriptor {
    constructor(descriptor: InternalTaskDescriptor, parent: OperationDescriptor?, taskPath: String?) : this(descriptor, parent, taskPath, unsupportedDependencies(), unsupportedOriginPlugin())

    constructor(
        descriptor: InternalTaskDescriptor,
        parent: OperationDescriptor?,
        taskPath: String?,
        dependencies: MutableSet<OperationDescriptor?>?,
        originPlugin: PluginIdentifier?
    ) : this(descriptor, parent, taskPath, Suppliers.ofInstance<MutableSet<OperationDescriptor?>?>(dependencies), Suppliers.ofInstance<PluginIdentifier?>(originPlugin))



    override val dependencies: MutableSet<out OperationDescriptor?>?
        get() = dependenciesSupplier.get()

    override val originPlugin: PluginIdentifier?
        get() = originPluginSupplier.get()

    companion object {
        private val DEPENDENCIES_METHOD = TaskOperationDescriptor::class.java.getSimpleName() + ".dependencies"
        private val ORIGIN_PLUGIN_METHOD = TaskOperationDescriptor::class.java.getSimpleName() + ".originPlugin"

        private fun unsupportedOriginPlugin(): Supplier<PluginIdentifier?> {
            return unsupportedMethodExceptionThrowingSupplier<PluginIdentifier?>(ORIGIN_PLUGIN_METHOD)
        }

        private fun unsupportedDependencies(): Supplier<MutableSet<OperationDescriptor?>?> {
            return unsupportedMethodExceptionThrowingSupplier<MutableSet<OperationDescriptor?>?>(DEPENDENCIES_METHOD)
        }

        private fun <T> unsupportedMethodExceptionThrowingSupplier(method: String?): Supplier<T?> {
            return object : Supplier<T?> {
                override fun get(): T? {
                    throw Exceptions.unsupportedMethod(method)
                }
            }
        }
    }
}
