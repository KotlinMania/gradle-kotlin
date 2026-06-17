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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import com.google.common.base.Objects
import org.gradle.api.Describable
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.internal.Describables

class DefaultComponentSelectionDescriptor private constructor(
    private val cause: ComponentSelectionCause,
    private val description: Describable,
    private val hasCustomDescription: Boolean,
    private val isEquivalentToForce: Boolean
) : ComponentSelectionDescriptorInternal {
    private val hashCode: Int

    constructor(cause: ComponentSelectionCause) : this(cause, Describables.of(cause.getDefaultReason()), false, cause == ComponentSelectionCause.FORCED)

    constructor(cause: ComponentSelectionCause, description: Describable) : this(cause, description, true, cause == ComponentSelectionCause.FORCED)

    init {
        if (hasCustomDescription) {
            this.hashCode = 31 * (31 * cause.hashCode() + description.hashCode()) + (if (isEquivalentToForce) 1 else 0)
        } else {
            this.hashCode = 31 * cause.hashCode() + (if (isEquivalentToForce) 1 else 0)
        }
    }

    override fun getCause(): ComponentSelectionCause {
        return cause
    }

    override fun getDescription(): String {
        return description.getDisplayName()
    }

    override fun hasCustomDescription(): Boolean {
        return hasCustomDescription
    }

    override fun getDescribable(): Describable {
        return description
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultComponentSelectionDescriptor
        return hashCode == that.hashCode && cause == that.cause && isEquivalentToForce == that.isEquivalentToForce && Objects.equal(description, that.description)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return description.getDisplayName()
    }

    override fun withDescription(description: Describable): ComponentSelectionDescriptorInternal {
        if (this.description == description) {
            return this
        }
        return DefaultComponentSelectionDescriptor(cause, description, true, isEquivalentToForce)
    }

    override fun markAsEquivalentToForce(): ComponentSelectionDescriptorInternal {
        return DefaultComponentSelectionDescriptor(cause, description, hasCustomDescription, true)
    }

    override fun isEquivalentToForce(): Boolean {
        return isEquivalentToForce
    }
}
