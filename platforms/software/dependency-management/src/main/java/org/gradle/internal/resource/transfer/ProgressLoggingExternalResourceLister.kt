/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.resource.transfer

import org.gradle.api.resources.ResourceException
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.ExternalResourceListBuildOperationType
import org.gradle.internal.resource.ExternalResourceName
import java.net.URI

class ProgressLoggingExternalResourceLister(private val delegate: ExternalResourceLister, private val buildOperationExecutor: BuildOperationRunner) : AbstractProgressLoggingHandler(),
    ExternalResourceLister {
    @Throws(ResourceException::class)
    override fun list(parent: ExternalResourceName): MutableList<String>? {
        return buildOperationExecutor.call<MutableList<String>>(ProgressLoggingExternalResourceLister.ListOperation(parent))
    }

    private class ListOperationDetails(location: URI) : LocationDetails(location), ExternalResourceListBuildOperationType.Details {
        override fun toString(): String {
            return "ExternalResourceListBuildOperationType.Details{location=" + getLocation() + ", " + '}'
        }
    }

    private inner class ListOperation(private val parent: ExternalResourceName) : CallableBuildOperation<MutableList<String>> {
        override fun call(context: BuildOperationContext): MutableList<String> {
            try {
                return delegate.list(parent)!!
            } finally {
                context.setResult(LIST_RESULT)
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName("List " + parent.getUri())
                .details(ListOperationDetails(parent.getUri()))
        }
    }

    companion object {
        private val LIST_RESULT: ExternalResourceListBuildOperationType.Result = object : ExternalResourceListBuildOperationType.Result {
        }
    }
}
