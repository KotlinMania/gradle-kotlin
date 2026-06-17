/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.internal.resolve.ModuleVersionResolveException
import java.lang.Boolean
import kotlin.Any
import kotlin.Int
import kotlin.String

class DefaultUnresolvedDependencyResult(
    private val requested: ComponentSelector,
    private val from: ResolvedComponentResult,
    private val constraint: Boolean,
    private val failure: ModuleVersionResolveException,
    private val reason: ComponentSelectionReason
) : UnresolvedDependencyResult {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode(requested, from, constraint, failure, reason)
    }

    override fun getRequested(): ComponentSelector {
        return requested
    }

    override fun getFrom(): ResolvedComponentResult {
        return from
    }

    override fun isConstraint(): Boolean {
        return constraint
    }

    override fun getFailure(): ModuleVersionResolveException {
        return failure
    }

    override fun getAttempted(): ComponentSelector {
        return failure.selector!!
    }

    override fun getAttemptedReason(): ComponentSelectionReason {
        return reason
    }

    override fun toString(): String {
        return getRequested().toString() + " -> " + getAttempted() + " - " + failure.getMessage()
    }

    override fun equals(o: Any?): Boolean {
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultUnresolvedDependencyResult
        return requested == that.requested &&
                from == that.from && constraint == that.constraint &&
                failure == that.failure &&
                reason == that.reason
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private fun computeHashCode(
            requested: ComponentSelector,
            from: ResolvedComponentResult,
            constraint: Boolean,
            failure: ModuleVersionResolveException,
            reason: ComponentSelectionReason
        ): Int {
            var result = requested.hashCode()
            result = 31 * result + from.hashCode()
            result = 31 * result + Boolean.hashCode(constraint)
            result = 31 * result + failure.hashCode()
            result = 31 * result + reason.hashCode()
            return result
        }
    }
}
