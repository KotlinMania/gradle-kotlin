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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor

object ComponentSelectionReasons {
    val REQUESTED: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.REQUESTED)
    val ROOT: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.ROOT)
    val FORCED: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.FORCED)
    val CONFLICT_RESOLUTION: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONFLICT_RESOLUTION)
    val SELECTED_BY_RULE: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.SELECTED_BY_RULE)
    val COMPOSITE_BUILD: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.COMPOSITE_BUILD)
    val CONSTRAINT: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT)
    val REJECTION: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.REJECTION)
    val BY_ANCESTOR: ComponentSelectionDescriptorInternal = DefaultComponentSelectionDescriptor(ComponentSelectionCause.BY_ANCESTOR)

    fun requested(): ComponentSelectionReasonInternal {
        return of(REQUESTED)
    }

    fun root(): ComponentSelectionReasonInternal {
        return of(ROOT)
    }

    fun of(descriptions: ComponentSelectionDescriptorInternal): ComponentSelectionReasonInternal {
        return DefaultComponentSelectionReason(ImmutableList.of<ComponentSelectionDescriptorInternal>(descriptions))
    }

    fun of(dependencyReasons: ImmutableSet<ComponentSelectionDescriptorInternal>): ComponentSelectionReasonInternal {
        assert(!dependencyReasons.isEmpty())
        return DefaultComponentSelectionReason(dependencyReasons.asList())
    }

    fun isCauseExpected(descriptor: ComponentSelectionDescriptor): Boolean {
        return descriptor.getCause() == ComponentSelectionCause.REQUESTED || descriptor.getCause() == ComponentSelectionCause.ROOT
    }

    internal class DefaultComponentSelectionReason // Package private since static factories enforce non-empty and no duplicates,
    // but the serializer can skip the set creation.
        (private val descriptions: ImmutableList<ComponentSelectionDescriptorInternal>) : ComponentSelectionReasonInternal {
        override fun isForced(): Boolean {
            return hasCause(ComponentSelectionCause.FORCED)
        }

        private fun hasCause(cause: ComponentSelectionCause): Boolean {
            for (description in descriptions) {
                if (description.getCause() == cause) {
                    return true
                }
            }
            return false
        }

        override fun isConflictResolution(): Boolean {
            return hasCause(ComponentSelectionCause.CONFLICT_RESOLUTION)
        }

        override fun isSelectedByRule(): Boolean {
            return hasCause(ComponentSelectionCause.SELECTED_BY_RULE)
        }

        override fun isExpected(): Boolean {
            return descriptions.size == 1 && isCauseExpected(this.last)
        }

        override fun isCompositeSubstitution(): Boolean {
            return hasCause(ComponentSelectionCause.COMPOSITE_BUILD)
        }

        override fun toString(): String {
            return this.last.toString()
        }

        override fun getDescriptions(): MutableList<ComponentSelectionDescriptorInternal> {
            return descriptions
        }

        override fun isConstrained(): Boolean {
            return hasCause(ComponentSelectionCause.CONSTRAINT)
        }

        override fun hasCustomDescriptions(): Boolean {
            for (description in descriptions) {
                if (description.hasCustomDescription()) {
                    return true
                }
            }
            return false
        }

        private val last: ComponentSelectionDescriptorInternal
            get() = descriptions.get(descriptions.size - 1)

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as DefaultComponentSelectionReason
            return descriptions == that.descriptions
        }

        override fun hashCode(): Int {
            return descriptions.hashCode()
        }
    }
}
