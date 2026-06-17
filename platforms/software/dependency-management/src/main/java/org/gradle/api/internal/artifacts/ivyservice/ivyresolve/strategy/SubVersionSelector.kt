/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

/**
 * Version matcher for dynamic version selectors ending in '+'.
 */
class SubVersionSelector(selector: String) : AbstractStringVersionSelector(selector) {
    val prefix: String

    init {
        prefix = selector.substring(0, selector.length - 1)
    }

    override fun isDynamic(): Boolean {
        return true
    }

    override fun requiresMetadata(): Boolean {
        return false
    }

    override fun matchesUniqueVersion(): Boolean {
        return false
    }

    override fun accept(candidate: String): Boolean {
        return candidate.startsWith(prefix)
    }

    override fun canShortCircuitWhenVersionAlreadyPreselected(): Boolean {
        return false
    }
}
