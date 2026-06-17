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
package org.gradle.internal.component.external.descriptor

import com.google.common.base.Objects
import org.gradle.util.internal.CollectionUtils.toList

class Configuration(@JvmField val name: String?, val isTransitive: Boolean, val isVisible: Boolean, extendsFrom: MutableCollection<String?>?) {
    @JvmField
    val extendsFrom: MutableList<String?>

    init {
        this.extendsFrom = toList<String?>(extendsFrom)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as Configuration
        return this.isTransitive == that.isTransitive && this.isVisible == that.isVisible && Objects.equal(name, that.name)
                && Objects.equal(extendsFrom, that.extendsFrom)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(name, this.isTransitive, this.isVisible, extendsFrom)
    }
}
