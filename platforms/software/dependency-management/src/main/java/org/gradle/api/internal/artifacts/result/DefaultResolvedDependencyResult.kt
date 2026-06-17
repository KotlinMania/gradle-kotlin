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
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import java.lang.Boolean
import kotlin.Any
import kotlin.Int
import kotlin.String

class DefaultResolvedDependencyResult(
    private val requested: ComponentSelector,
    private val constraint: Boolean,
    private val from: ResolvedComponentResult,
    private val selectedComponent: ResolvedComponentResult,
    private val selectedVariant: ResolvedVariantResult
) : ResolvedDependencyResult {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode(requested, from, constraint, selectedComponent, selectedVariant)
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

    override fun getSelected(): ResolvedComponentResult {
        return selectedComponent
    }

    override fun getResolvedVariant(): ResolvedVariantResult {
        return selectedVariant
    }

    override fun toString(): String {
        if (getRequested().matchesStrictly(getSelected().getId())) {
            return getRequested().toString()
        } else {
            return getRequested().toString() + " -> " + getSelected().getId()
        }
    }

    override fun equals(o: Any?): Boolean {
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultResolvedDependencyResult
        return requested == that.requested &&
                from == that.from && constraint == that.constraint &&
                selectedComponent == that.selectedComponent &&
                selectedVariant == that.selectedVariant
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private fun computeHashCode(
            requested: ComponentSelector,
            from: ResolvedComponentResult,
            constraint: Boolean,
            selectedComponent: ResolvedComponentResult,
            selectedVariant: ResolvedVariantResult
        ): Int {
            var result = requested.hashCode()
            result = 31 * result + from.hashCode()
            result = 31 * result + Boolean.hashCode(constraint)
            result = 31 * result + selectedComponent.hashCode()
            result = 31 * result + selectedVariant.hashCode()
            return result
        }
    }
}
