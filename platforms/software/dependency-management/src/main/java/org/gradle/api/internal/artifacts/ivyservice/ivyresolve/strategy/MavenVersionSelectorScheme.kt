/*
 * Copyright 2014 the original author or authors.
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

class MavenVersionSelectorScheme(private val defaultVersionSelectorScheme: VersionSelectorScheme) : VersionSelectorScheme {
    override fun parseSelector(selectorString: String): VersionSelector? {
        if (selectorString == RELEASE) {
            return LatestVersionSelector(LATEST_RELEASE)
        } else if (selectorString == LATEST) {
            return LatestVersionSelector(LATEST_INTEGRATION)
        } else {
            return defaultVersionSelectorScheme.parseSelector(selectorString)
        }
    }

    override fun renderSelector(selector: VersionSelector?): String {
        return toMavenSyntax(defaultVersionSelectorScheme.renderSelector(selector))
    }

    override fun complementForRejection(selector: VersionSelector?): VersionSelector? {
        return defaultVersionSelectorScheme.complementForRejection(selector)
    }

    // TODO: VersionSelector should be more descriptive, so it can be directly translated
    private fun toMavenSyntax(version: String): String {
        var version = version
        if (version == LATEST_INTEGRATION) {
            return LATEST
        }
        if (version == LATEST_RELEASE) {
            return RELEASE
        }
        if (version.startsWith("]")) {
            version = '('.toString() + version.substring(1)
        }
        if (version.endsWith("[")) {
            version = version.substring(0, version.length - 1) + ')'
        }
        return version
    }

    companion object {
        const val LATEST: String = "LATEST"
        const val RELEASE: String = "RELEASE"
        private const val LATEST_INTEGRATION = "latest.integration"
        private const val LATEST_RELEASE = "latest.release"

        @JvmStatic
        fun isSubstituableLatest(version: String): Boolean {
            if (version == LATEST_INTEGRATION || version == LATEST_RELEASE) {
                return true
            }
            require(version.contains("latest")) { "The provided version does not contain 'latest'" }
            return false
        }
    }
}
