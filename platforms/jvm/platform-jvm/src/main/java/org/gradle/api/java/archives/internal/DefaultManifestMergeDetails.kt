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
package org.gradle.api.java.archives.internal

import org.gradle.api.java.archives.ManifestMergeDetails

class DefaultManifestMergeDetails(private val section: String?, private val key: String?, private val baseValue: String?, private val mergeValue: String?, private var value: String?) :
    ManifestMergeDetails {
    var isExcluded: Boolean = false
        private set

    override fun getSection(): String? {
        return section
    }

    override fun getKey(): String? {
        return key
    }

    override fun getBaseValue(): String? {
        return baseValue
    }

    override fun getMergeValue(): String? {
        return mergeValue
    }

    override fun getValue(): String? {
        return value
    }

    override fun setValue(value: String?) {
        this.value = value
    }

    override fun exclude() {
        this.isExcluded = true
    }
}
