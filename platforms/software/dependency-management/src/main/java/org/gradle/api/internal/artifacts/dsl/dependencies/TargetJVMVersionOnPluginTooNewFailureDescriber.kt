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
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import java.util.function.Supplier

/**
 * A [ResolutionFailureDescriber] that describes a [ResolutionFailure] caused by a requested **plugin**
 * requiring a higher JVM version than the current build is using.
 *
 * This is determined by assessing the incompatibility of the [TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE]
 * attribute on the requested plugin and comparing the provided JVM version with the current JVM version.  Note that the requested
 * JVM version is **NOT** relevant to this failure - it's about an incompatibility
 * due to the candidate plugin's JVM version being too high to work on this JVM, regardless of the requested version.
 *
 * Whether a request is a plugin request or not is determined by the presence of the [GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE]
 * attribute.
 */
abstract class TargetJVMVersionOnPluginTooNewFailureDescriber : AbstractJVMVersionTooNewFailureDescriber() {
    private val currentJVMVersion: JavaVersion = current()!!

    override fun getJVMVersion(failure: NoCompatibleVariantsFailure): JavaVersion {
        return currentJVMVersion
    }

    override fun canDescribeFailure(failure: NoCompatibleVariantsFailure): Boolean {
        val isPluginRequest = failure.getRequestedAttributes().contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE)
        return isPluginRequest && isDueToJVMVersionTooNew(failure)
    }

    override fun describeFailure(failure: NoCompatibleVariantsFailure): AbstractResolutionFailureException {
        val minJVMVersionSupported = findMinJVMSupported(failure.candidates).orElseThrow<java.lang.IllegalStateException>(Supplier { IllegalStateException() })
        val message = buildNeedsNewerJDKFailureMsg(minJVMVersionSupported)
        val resolutions = buildResolutions(suggestUpdateJVM(minJVMVersionSupported))
        return VariantSelectionByAttributesException(message, failure, resolutions)
    }

    private fun buildNeedsNewerJDKFailureMsg(minRequiredJVMVersion: JavaVersion): String {
        return String.format(JVM_VERSION_TOO_HIGH_TEMPLATE, minRequiredJVMVersion.majorVersion, currentJVMVersion.majorVersion)
    }

    private fun suggestUpdateJVM(minRequiredJVMVersion: JavaVersion): String {
        return "Run this build using a Java " + minRequiredJVMVersion.majorVersion + " or newer JVM."
    }

    companion object {
        private const val JVM_VERSION_TOO_HIGH_TEMPLATE = "Dependency requires at least JVM runtime version %s. This build uses a Java %s JVM."
    }
}
