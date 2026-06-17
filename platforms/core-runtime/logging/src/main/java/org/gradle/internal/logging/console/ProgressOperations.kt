/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.console

import org.gradle.internal.operations.OperationIdentifier

class ProgressOperations {
    private val operationsById: MutableMap<OperationIdentifier, ProgressOperation> = HashMap<OperationIdentifier, ProgressOperation>()

    fun start(status: String, category: String, operationId: OperationIdentifier, parentOperationId: OperationIdentifier?): ProgressOperation {
        var parent: ProgressOperation? = null
        if (parentOperationId != null) {
            parent = operationsById.get(parentOperationId)
        }
        val operation = ProgressOperation(status, category, operationId, parent!!)
        if (parent != null) {
            parent.addChild(operation)
        }
        val previous = operationsById.put(operationId, operation)
        check(previous == null) { "Received start event for an operation that has already started (id: " + operationId + "). Currently in progress=" + operationsById.values }
        return operation
    }

    fun progress(description: String, operationId: OperationIdentifier): ProgressOperation {
        val op: ProgressOperation = operationsById.get(operationId)!!
        checkNotNull(op) { "Received progress event for an unknown operation (id: " + operationId + "). Currently in progress=" + operationsById.values }
        op.setStatus(description)
        return op
    }

    fun complete(operationId: OperationIdentifier): ProgressOperation {
        val op: ProgressOperation = operationsById.remove(operationId)!!
        checkNotNull(op) { "Received complete event for an unknown operation (id: " + operationId + "). Currently in progress=" + operationsById.values }
        if (op.getParent() != null) {
            op.getParent().removeChild(op)
        }
        return op
    }
}
