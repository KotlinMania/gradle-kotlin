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
package org.gradle.api.publish.internal.validation

/**
 * Collects all publication warnings for a single variant.
 */
class VariantWarningCollector {
    var unsupportedUsages: MutableSet<String?>? = null
        private set
    var incompatibleUsages: MutableSet<String?>? = null
        private set
    var variantUnsupported: MutableSet<String?>? = null
        private set

    fun addUnsupported(text: String?) {
        if (unsupportedUsages == null) {
            unsupportedUsages = HashSet<String?>()
        }
        unsupportedUsages!!.add(text)
    }

    fun addIncompatible(text: String?) {
        if (incompatibleUsages == null) {
            incompatibleUsages = HashSet<String?>()
        }
        incompatibleUsages!!.add(text)
    }

    val isEmpty: Boolean
        get() = incompatibleUsages == null && unsupportedUsages == null && variantUnsupported == null

    fun addVariantUnsupported(text: String?) {
        if (variantUnsupported == null) {
            variantUnsupported = HashSet<String?>()
        }
        variantUnsupported!!.add(text)
    }
}
