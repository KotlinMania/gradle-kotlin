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
package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import java.util.function.Supplier

/**
 * A [ResolutionFailureDescriber] that describes a [ResolutionFailure] caused by a requested library
 * (**non-plugin**) requiring a higher JVM version.
 *
 * This is determined by assessing the incompatibility of the [TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE]
 * attribute on the requested library and comparing the provided JVM version with the requested JVM version.  Note that the version
 * of the running JVM is **NOT** relevant to this failure.
 *
 * Whether a request is a plugin request or not is determined by the presence of the [GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE]
 * attribute.
 */
abstract class TargetJVMVersionOnLibraryTooNewFailureDescriber : AbstractJVMVersionTooNewFailureDescriber() {
    override fun getJVMVersion(failure: NoCompatibleVariantsFailure): JavaVersion {
        val jvmVersionEntry = failure.getRequestedAttributes().findEntry<Int>(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
        return toVersion(jvmVersionEntry!!.getIsolatedValue())!!
    }

    override fun canDescribeFailure(failure: NoCompatibleVariantsFailure): Boolean {
        val isPluginRequest = failure.getRequestedAttributes().contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE)
        return !isPluginRequest && isDueToJVMVersionTooNew(failure)
    }

    override fun describeFailure(failure: NoCompatibleVariantsFailure): AbstractResolutionFailureException {
        val minJVMVersionSupported = findMinJVMSupported(failure.candidates).orElseThrow<java.lang.IllegalStateException>(Supplier { IllegalStateException() })
        val requestedJVMVersion = getJVMVersion(failure)

        val message = buildNeedsNewerJDKFailureMsg(failure.describeRequestTarget(), minJVMVersionSupported, failure)
        val resolutions = buildResolutions(suggestChangeLibraryVersion(failure.describeRequestTarget(), requestedJVMVersion))
        return VariantSelectionByAttributesException(message, failure, resolutions)
    }

    private fun buildNeedsNewerJDKFailureMsg(requestedName: String, minRequiredJVMVersion: JavaVersion, failure: NoCompatibleVariantsFailure): String {
        return String.format(JVM_VERSION_TOO_HIGH_TEMPLATE, getJVMVersion(failure).majorVersion, requestedName, minRequiredJVMVersion.majorVersion)
    }

    private fun suggestChangeLibraryVersion(requestedName: String, minRequiredJVMVersion: JavaVersion): String {
        return "Change the dependency on '" + requestedName + "' to an earlier version that supports JVM runtime version " + minRequiredJVMVersion.majorVersion + "."
    }

    companion object {
        private const val JVM_VERSION_TOO_HIGH_TEMPLATE =
            "Dependency resolution is looking for a library compatible with JVM runtime version %s, but '%s' is only compatible with JVM runtime version %s or newer."
    }
}
