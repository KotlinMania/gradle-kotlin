/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.tasks.wrapper.internal

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.GradleException
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.TextResourceFactory
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DistributionLocator
import org.jspecify.annotations.NullMarked
import java.util.Arrays
import java.util.Objects
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors

class GradleVersionResolver(private val textResourceFactory: TextResourceFactory) {
    private var gradleVersion: GradleVersion? = null
    private var gradleVersionRequest = GradleVersionRequest(GradleVersion.current())

    fun getGradleVersion(): GradleVersion {
        if (gradleVersion == null) {
            gradleVersion = resolve()
        }
        return gradleVersion!!
    }

    fun setGradleVersionRequest(request: String) {
        val gradleVersionRequest = GradleVersionRequest(request)
        if (gradleVersionRequest.requestType == RequestType.VERSION) {
            this.gradleVersion = parseVersionString(request)
        } else if (this.gradleVersionRequest != gradleVersionRequest) {
            this.gradleVersion = null
            this.gradleVersionRequest = gradleVersionRequest
        }
    }

    private fun resolve(): GradleVersion {
        when (gradleVersionRequest.requestType) {
            RequestType.DYNAMIC_VERSION -> {
                val version = getSingleVersion(gradleVersionRequest.dynamicVersion!!)
                return GradleVersion.version(version)
            }

            RequestType.SEMANTIC_VERSION -> return resolveSemanticVersion(gradleVersionRequest.majorVersion!!, gradleVersionRequest.minorVersion)
            RequestType.VERSION -> return GradleVersion.version(gradleVersionRequest.request)
            else -> throw IllegalArgumentException("Unknown request type: " + gradleVersionRequest.requestType)
        }
    }

    private fun resolveSemanticVersion(majorVersion: Int, minorVersion: Int?): GradleVersion {
        val versions = getVersionsList(majorVersion.toString())
            .stream()
            .map<GradleVersion> { v: String? ->
                try {
                    return@map GradleVersion.version(v)
                } catch (e: Exception) {
                    return@map null
                }
            }
            .filter { obj: GradleVersion? -> Objects.nonNull(obj) }
            .filter { v: GradleVersion -> v.isFinal() && v.getMajorVersion() == majorVersion }

        if (minorVersion == null) {
            return versions!!.max(Comparator { obj: GradleVersion, o: GradleVersion -> obj.compareTo(o) }).orElseThrow<WrapperVersionException>(Supplier {
                WrapperVersionException(
                    "Invalid version specified for argument '--gradle-version': no final version found for major version " + majorVersion,
                    null
                )
            }
            )
        } else {
            return versions!!
                .filter { v: GradleVersion -> getMinorVersion(v) == minorVersion }
                .max(Comparator { obj: GradleVersion, o: GradleVersion -> obj.compareTo(o) }).orElseThrow<WrapperVersionException>(Supplier {
                    WrapperVersionException(
                        "Invalid version specified for argument '--gradle-version': no final version found for version " + majorVersion + "." + minorVersion,
                        null
                    )
                }
                )
        }
    }

    private fun getApiEndpoint(request: String): String {
        return DistributionLocator.getBaseUrl() + "/versions/" + request
    }

    private fun getSingleVersion(dynamicVersion: DynamicVersion): String {
        try {
            return getVersion(textResourceFactory.fromUri(getApiEndpoint(dynamicVersion.urlSuffix)).asString(), dynamicVersion.name)
        } catch (e: MissingResourceException) {
            // swallowing the original exception to provide a more user-friendly message
            throw WrapperVersionException("Unable to resolve Gradle version for '" + dynamicVersion.name + "'.", null)
        }
    }

    private fun getVersionsList(majorVersion: String): MutableList<String> {
        try {
            return getVersions(textResourceFactory.fromUri(getApiEndpoint(majorVersion)).asString())
        } catch (e: MissingResourceException) {
            // swallowing the original exception to provide a more user-friendly message
            throw WrapperVersionException("Unable to resolve list of Gradle versions for '" + majorVersion + "'.", null)
        }
    }

    /**
     * This exception is thrown when the wrapper task is run in an attempt to update the wrapper version and
     * an invalid version is specified.
     */
    class WrapperVersionException(message: String, cause: Throwable?) : GradleException(message, cause), ResolutionProvider {
        val resolutions: MutableList<String>
            get() = Arrays.asList<String>(
                suggestActualVersion(),
                suggestDynamicVersions()
            )

        companion object {
            private fun suggestActualVersion(): String {
                return "Specify a valid Gradle release listed on https://gradle.org/releases/."
            }

            private fun suggestDynamicVersions(): String {
                val validStrings = Arrays.stream<DynamicVersion>(DynamicVersion.entries.toTypedArray())
                    .map<String> { dv: DynamicVersion? -> dv.name }
                    .map<String> { s: String? -> String.format("'%s'", s) }
                    .collect(Collectors.joining(", "))
                return String.format("Use one of the following dynamic version specifications: %s.", validStrings)
            }
        }
    }

    private enum class DynamicVersion(private val name: String, private val urlSuffix: String) {
        LATEST("latest", "current"),
        RELEASE_CANDIDATE("release-candidate", "release-candidate"),
        RELEASE_MILESTONE("release-milestone", "milestone"),
        RELEASE_NIGHTLY("release-nightly", "release-nightly"),
        NIGHTLY("nightly", "nightly");

        companion object {
            fun findMatch(version: String): DynamicVersion? {
                return Arrays.stream<DynamicVersion>(entries.toTypedArray()).filter { dv: DynamicVersion? -> dv!!.name == version }.findFirst().orElse(null)
            }
        }
    }

    @NullMarked
    private enum class RequestType {
        DYNAMIC_VERSION,
        SEMANTIC_VERSION,
        VERSION
    }

    @NullMarked
    private class GradleVersionRequest {
        val request: String
        val requestType: RequestType
        var dynamicVersion: DynamicVersion? = null
        var majorVersion: Int? = null
        var minorVersion: Int? = null

        internal constructor(request: String) {
            this.request = request
            val dynamicVersion: DynamicVersion? = DynamicVersion.Companion.findMatch(request)
            if (dynamicVersion != null) {
                this.requestType = RequestType.DYNAMIC_VERSION
                this.dynamicVersion = dynamicVersion
            } else {
                val matcher: Matcher = SEMVER_REQUEST.matcher(request)
                if (matcher.matches()) {
                    majorVersion = matcher.group(1).toInt()
                    minorVersion = if (matcher.group(3) != null) matcher.group(3).toInt() else null
                    if (majorVersion!! >= 9) {
                        this.requestType = RequestType.SEMANTIC_VERSION
                    } else {
                        this.requestType = RequestType.VERSION
                    }
                } else {
                    this.requestType = RequestType.VERSION
                }
            }
        }

        internal constructor(gradleVersion: GradleVersion) {
            this.request = gradleVersion.getVersion()
            this.requestType = RequestType.VERSION
        }

        override fun equals(other: Any): Boolean {
            if (this === other) {
                return true
            }
            if (other !is GradleVersionRequest) {
                return false
            }
            return request == other.request
        }

        override fun hashCode(): Int {
            return Objects.hashCode(request)
        }

        companion object {
            private val SEMVER_REQUEST: Pattern = Pattern.compile("([0-9]+)(\\.([0-9]+))?")
        }
    }

    companion object {
        private fun getMinorVersion(version: GradleVersion): Int {
            val versionParts = version.getBaseVersion().getVersion().split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (versionParts.size > 1) {
                return versionParts[1].toInt()
            } else {
                return 0
            }
        }

        fun getVersion(json: String, request: String): String {
            val type = object : TypeToken<MutableMap<String, String>>() {}.getType()
            val map = Gson().fromJson<MutableMap<String, String>>(json, type)
            val version: String = map.get("version")!!
            if (version == null) {
                throw GradleException("There is currently no version information available for '" + request + "'.")
            }
            return version
        }

        fun getVersions(json: String): MutableList<String> {
            val type = object : TypeToken<MutableList<MutableMap<String, String>>>() {}.getType()
            val map = Gson().fromJson<MutableList<MutableMap<String, String>>>(json, type)
            return map.stream()
                .map<String> { m: MutableMap<String?, String?>? -> m!!.get("version") }
                .filter { obj: String? -> Objects.nonNull(obj) }
                .collect(Collectors.toList())
        }

        private fun parseVersionString(gradleVersionString: String): GradleVersion {
            try {
                return GradleVersion.version(gradleVersionString)
            } catch (e: Exception) {
                throw WrapperVersionException("Invalid version specified for argument '--gradle-version'", e)
            }
        }
    }
}
