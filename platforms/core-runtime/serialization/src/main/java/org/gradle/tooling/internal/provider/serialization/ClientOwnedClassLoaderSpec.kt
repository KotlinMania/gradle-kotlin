/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.classloader.ClassLoaderSpec
import java.net.URI

class ClientOwnedClassLoaderSpec(@JvmField val classpath: MutableList<URI>) : ClassLoaderSpec() {
    override fun toString(): String {
        return "{client-owned-class-loader classpath: " + classpath + "}"
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as ClientOwnedClassLoaderSpec
        return classpath == other.classpath
    }

    override fun hashCode(): Int {
        return classpath.hashCode()
    }
}
