/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model.internal

import com.google.common.base.Objects
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.plugins.ide.eclipse.model.ResourceFilterMatcher
import org.gradle.util.internal.ClosureBackedAction

class DefaultResourceFilterMatcher() : ResourceFilterMatcher {
    private var id: String? = null
    private var arguments: String? = null
    private var children: MutableSet<ResourceFilterMatcher?>? = LinkedHashSet<ResourceFilterMatcher?>()

    constructor(id: String?, arguments: String?, children: MutableSet<ResourceFilterMatcher?>?) : this() {
        this.id = id
        this.arguments = arguments
        this.children = children
    }

    override fun getId(): String? {
        return id
    }

    override fun setId(id: String?) {
        this.id = id
    }

    override fun getArguments(): String? {
        return arguments
    }

    override fun setArguments(arguments: String?) {
        this.arguments = arguments
    }

    override fun getChildren(): MutableSet<ResourceFilterMatcher?>? {
        return children
    }

    fun setChildren(children: MutableSet<ResourceFilterMatcher?>) {
        if (children == null) {
            throw InvalidUserDataException("children must not be null")
        }
        this.children = children
    }

    fun matcher(@DelegatesTo(value = ResourceFilterMatcher::class, strategy = Closure.DELEGATE_FIRST) configureClosure: Closure<*>?): ResourceFilterMatcher {
        return matcher(ClosureBackedAction<ResourceFilterMatcher?>(configureClosure))
    }

    override fun matcher(configureAction: Action<in ResourceFilterMatcher?>): ResourceFilterMatcher {
        val m: ResourceFilterMatcher = DefaultResourceFilterMatcher()
        configureAction.execute(m)
        children!!.add(m)
        return m
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null) {
            return false
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val resourceFilterMatcher = o as DefaultResourceFilterMatcher
        return Objects.equal(id, resourceFilterMatcher.id)
                && Objects.equal(arguments, resourceFilterMatcher.arguments)
                && Objects.equal(children, resourceFilterMatcher.children)
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (id != null) id.hashCode() else 0
        result = 31 * result + (if (arguments != null) arguments.hashCode() else 0)
        result = 31 * result + (if (children != null) children.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return ("ResourceFilterMatcher{"
                + "id='" + id + '\''
                + ", arguments='" + arguments + '\''
                + ", children='" + children + '\''
                + '}')
    }
}
