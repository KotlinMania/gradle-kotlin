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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ComponentMetadata

/**
 * A version selector which is the union of other selectors. This is used by the
 * "rejects" clauses of version constraints, where each reject is turned into a
 * version selector, then the whole selector is the union of all of them.
 */
class UnionVersionSelector(private val selectors: MutableList<VersionSelector>) : CompositeVersionSelector {
    override fun isDynamic(): Boolean {
        for (selector in selectors) {
            if (selector.isDynamic()) {
                return true
            }
        }
        return false
    }

    override fun requiresMetadata(): Boolean {
        for (selector in selectors) {
            if (selector.requiresMetadata()) {
                return true
            }
        }
        return false
    }

    override fun matchesUniqueVersion(): Boolean {
        for (selector in selectors) {
            if (!selector.matchesUniqueVersion()) {
                return false
            }
        }
        return true
    }

    override fun accept(candidate: String?): Boolean {
        for (selector in selectors) {
            if (selector.accept(candidate)) {
                return true
            }
        }
        return false
    }

    override fun accept(candidate: Version?): Boolean {
        for (selector in selectors) {
            if (selector.accept(candidate)) {
                return true
            }
        }
        return false
    }

    override fun accept(candidate: ComponentMetadata?): Boolean {
        for (selector in selectors) {
            if (selector.accept(candidate)) {
                return true
            }
        }
        return false
    }

    override fun canShortCircuitWhenVersionAlreadyPreselected(): Boolean {
        for (selector in selectors) {
            if (!selector.canShortCircuitWhenVersionAlreadyPreselected()) {
                return false
            }
        }
        return true
    }

    override fun getSelector(): String? {
        throw UnsupportedOperationException("Union selectors should only be used internally and don't provide a public string representation")
    }

    override fun getSelectors(): MutableList<VersionSelector> {
        return selectors
    }

    companion object {
        fun of(selectors: MutableList<String?>, scheme: VersionSelectorScheme): UnionVersionSelector {
            val builder = ImmutableList.Builder<VersionSelector?>()
            for (selector in selectors) {
                builder.add(scheme.parseSelector(selector))
            }
            return UnionVersionSelector(builder.build())
        }
    }
}
