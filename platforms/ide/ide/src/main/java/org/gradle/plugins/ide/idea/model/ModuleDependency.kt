/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects
import com.google.common.base.Strings
import groovy.util.Node

/**
 * Represents an orderEntry of type module in the iml XML.
 */
class ModuleDependency(
    /**
     * The name of the module the module depends on.
     * Must not be null.
     */
    var name: String, private var scope: String?
) : Dependency {
    var isExported: Boolean = false

    /**
     * The scope for this dependency. If null the scope attribute is not added.
     */
    override fun getScope(): String? {
        return scope
    }

    override fun setScope(scope: String?) {
        this.scope = scope
    }

    override fun addToNode(parentNode: Node) {
        val attributes: MutableMap<String?, Any?> = LinkedHashMap<String?, Any?>()
        attributes.put("type", "module")
        attributes.put("module-name", name)
        if (this.isExported) {
            attributes.put("exported", "")
        }
        if (!Strings.isNullOrEmpty(scope) && "COMPILE" != scope) {
            attributes.put("scope", scope)
        }
        parentNode.appendNode("orderEntry", attributes)
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val that = o as ModuleDependency
        return Objects.equal(name, that.name) && scopeEquals(scope, that.scope)
    }

    override fun hashCode(): Int {
        var result: Int
        result = name.hashCode()
        result = 31 * result + this.scopeHash
        return result
    }

    private val scopeHash: Int
        get() = if (!Strings.isNullOrEmpty(scope) && scope != "COMPILE") scope.hashCode() else 0

    override fun toString(): String {
        return "ModuleDependency{" + "name='" + name + "\'" + ", scope='" + scope + "\'" + "}"
    }

    companion object {
        private fun scopeEquals(lhs: String?, rhs: String?): Boolean {
            if ("COMPILE" == lhs) {
                return Strings.isNullOrEmpty(rhs) || "COMPILE" == rhs
            } else if ("COMPILE" == rhs) {
                return Strings.isNullOrEmpty(lhs)
            } else {
                return Objects.equal(lhs, rhs)
            }
        }
    }
}
