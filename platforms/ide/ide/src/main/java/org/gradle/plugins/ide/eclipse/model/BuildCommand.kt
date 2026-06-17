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
package org.gradle.plugins.ide.eclipse.model

import com.google.common.base.Objects
import com.google.common.base.Preconditions
import java.io.Serializable

/**
 * A build command.
 */
class BuildCommand @JvmOverloads constructor(name: String?, arguments: MutableMap<String?, String?>? = LinkedHashMap<String?, String?>()) : Serializable {
    var name: String
    var arguments: MutableMap<String?, String?>?

    init {
        this.name = Preconditions.checkNotNull<String>(name)
        this.arguments = Preconditions.checkNotNull<MutableMap<String?, String?>?>(arguments)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as BuildCommand
        return Objects.equal(name, that.name) && Objects.equal(arguments, that.arguments)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(name, arguments)
    }

    override fun toString(): String {
        return "BuildCommand{name='" + name + "', arguments=" + arguments + "}"
    }
}
