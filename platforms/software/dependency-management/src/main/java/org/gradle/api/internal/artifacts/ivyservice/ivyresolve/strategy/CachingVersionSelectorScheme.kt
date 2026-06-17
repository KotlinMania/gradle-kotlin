/*
 * Copyright 2018 the original author or authors.
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

import java.util.concurrent.ConcurrentHashMap

class CachingVersionSelectorScheme(private val delegate: VersionSelectorScheme) : VersionSelectorScheme {
    private val cachedSelectors: MutableMap<String?, VersionSelector?> = ConcurrentHashMap<String?, VersionSelector?>()

    override fun parseSelector(selectorString: String?): VersionSelector? {
        var versionSelector = cachedSelectors.get(selectorString)
        if (versionSelector == null) {
            versionSelector = delegate.parseSelector(selectorString)
            cachedSelectors.put(selectorString, versionSelector)
        }
        return versionSelector
    }

    override fun renderSelector(selector: VersionSelector?): String? {
        return delegate.renderSelector(selector)
    }

    override fun complementForRejection(selector: VersionSelector?): VersionSelector? {
        return delegate.complementForRejection(selector)
    }
}
