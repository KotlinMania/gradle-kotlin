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
import org.gradle.util.internal.GUtil

class ProgressOperation(private var status: String, val category: String, @JvmField val operationId: OperationIdentifier, val parent: ProgressOperation?) {
    private var children: MutableSet<ProgressOperation>? = null

    override fun toString(): String {
        return String.format("id=%s, category=%s, status=%s", operationId, category, status)
    }

    fun setStatus(status: String) {
        this.status = status
    }

    val message: String?
        get() {
            if (GUtil.isTrue(status)) {
                return status
            }
            return null
        }

    fun addChild(operation: ProgressOperation): Boolean {
        if (children == null) {
            children = HashSet<ProgressOperation>()
        }
        return children!!.add(operation)
    }

    fun removeChild(operation: ProgressOperation): Boolean {
        checkNotNull(children) { String.format("Cannot remove child operation [%s] from operation with no children [%s]", operation, this) }
        return children!!.remove(operation)
    }

    fun hasChildren(): Boolean {
        return children != null && !children!!.isEmpty()
    }
}
