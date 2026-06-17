/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner

import org.gradle.execution.plan.Node
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import java.util.Objects
import java.util.function.Supplier
import java.util.stream.Collectors

internal class OperationDependenciesResolver {
    private val lookups: MutableList<OperationDependencyLookup?> = ArrayList<OperationDependencyLookup?>()

    fun addLookup(lookup: OperationDependencyLookup?) {
        lookups.add(lookup)
    }

    fun resolveDependencies(node: Node): MutableSet<InternalOperationDescriptor?> {
        return node.getDependencySuccessors().stream()
            .map<InternalOperationDescriptor?> { node: Node? -> this.lookupExistingOperationDescriptor(node) }
            .filter { obj: InternalOperationDescriptor? -> Objects.nonNull(obj) }
            .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
    }

    private fun lookupExistingOperationDescriptor(node: Node?): InternalOperationDescriptor? {
        return lookups.stream()
            .map<InternalOperationDescriptor?> { entry: OperationDependencyLookup? -> entry!!.lookupExistingOperationDescriptor(node) }
            .filter { obj: InternalOperationDescriptor? -> Objects.nonNull(obj) }
            .findFirst()
            .orElse(null)
    }
}
