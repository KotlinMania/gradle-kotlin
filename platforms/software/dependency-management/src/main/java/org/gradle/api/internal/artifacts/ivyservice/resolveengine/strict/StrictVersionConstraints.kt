/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.internal.collect.PersistentSet
import org.jspecify.annotations.NullMarked

@NullMarked
//TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
open class StrictVersionConstraints private constructor(val modules: PersistentSet<ModuleIdentifier>) {
    open val isEmpty: Boolean
        get() = this === EMPTY

    open fun contains(module: ModuleIdentifier): Boolean {
        return modules.contains(module)
    }

    open fun union(other: StrictVersionConstraints): StrictVersionConstraints {
        if (other === EMPTY) {
            return this
        }
        if (this === other) {
            // this happens quite a lot!
            return this
        }
        val union = this.modules.union<ModuleIdentifier>(other.modules)
        if (union === this.modules) {
            return this
        }
        return of(union)
    }

    open fun intersect(other: StrictVersionConstraints): StrictVersionConstraints {
        if (other === EMPTY) {
            return EMPTY
        }
        if (this === other) {
            return this
        }
        val intersect = this.modules.intersect(other.modules)
        if (intersect === this.modules) {
            return this
        }
        return of(intersect)
    }

    override fun toString(): String {
        return "modules=" + modules
    }

    open fun minus(other: StrictVersionConstraints): StrictVersionConstraints {
        if (other === EMPTY) {
            return this
        }
        if (this === other) {
            return EMPTY
        }
        val diff = this.modules.except(other.modules)
        if (diff === this.modules) {
            return this
        }
        return of(diff)
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as StrictVersionConstraints
        return modules == that.modules
    }

    override fun hashCode(): Int {
        return modules.hashCode()
    }

    companion object {
        val EMPTY: StrictVersionConstraints = object : StrictVersionConstraints(PersistentSet.of<ModuleIdentifier>()) {
            override fun union(other: StrictVersionConstraints): StrictVersionConstraints {
                return other
            }

            override fun intersect(other: StrictVersionConstraints): StrictVersionConstraints {
                return EMPTY
            }

            override fun minus(other: StrictVersionConstraints): StrictVersionConstraints {
                return EMPTY
            }

            override fun isEmpty(): Boolean {
                return true
            }

            override fun contains(module: ModuleIdentifier): Boolean {
                return false
            }

            override fun toString(): String {
                return "no modules"
            }
        }

        fun of(modules: PersistentSet<ModuleIdentifier>): StrictVersionConstraints {
            if (modules.isEmpty()) {
                return EMPTY
            }
            return StrictVersionConstraints(modules)
        }
    }
}
