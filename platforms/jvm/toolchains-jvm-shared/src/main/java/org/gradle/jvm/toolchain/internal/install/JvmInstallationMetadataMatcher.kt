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
package org.gradle.jvm.toolchain.internal.install

import com.google.common.collect.ImmutableSet
import org.gradle.internal.jvm.inspection.JavaInstallationCapability
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec.Companion.any
import java.util.function.Predicate

class JvmInstallationMetadataMatcher(
    languageVersion: JavaLanguageVersion,
    vendorSpec: JvmVendorSpec,
    jvmImplementation: JvmImplementation,
    requiredCapabilities: Set<JavaInstallationCapability>
) : Predicate<JvmInstallationMetadata> {
    private val languageVersion: JavaLanguageVersion
    private val vendorSpec: DefaultJvmVendorSpec
    private val jvmImplementation: JvmImplementation
    private val requiredCapabilities: Set<JavaInstallationCapability>

    init {
        this.languageVersion = languageVersion
        this.vendorSpec = vendorSpec as DefaultJvmVendorSpec
        this.jvmImplementation = jvmImplementation
        this.requiredCapabilities = ImmutableSet.copyOf<JavaInstallationCapability>(requiredCapabilities)
    }

    constructor(spec: JavaToolchainSpec, requiredCapabilities: Set<JavaInstallationCapability>) : this(
        spec.languageVersion.get(),
        spec.vendor.get(),
        spec.implementation.get(),
        requiredCapabilities
    )

    override fun test(metadata: JvmInstallationMetadata): Boolean {
        return hasMatchingMajorVersion(metadata) && vendorSpec.test(metadata) && hasRequiredCapabilities(metadata) && hasMatchingImplementation(metadata)
    }

    private fun hasMatchingMajorVersion(metadata: JvmInstallationMetadata): Boolean {
        val actualVersion = of(metadata.javaMajorVersion)
        return actualVersion == languageVersion
    }

    private fun hasRequiredCapabilities(metadata: JvmInstallationMetadata): Boolean {
        return metadata.capabilities!!.containsAll(requiredCapabilities)
    }

    private fun hasMatchingImplementation(metadata: JvmInstallationMetadata): Boolean {
        if (jvmImplementation == JvmImplementation.Companion.VENDOR_SPECIFIC) {
            return true
        }

        val j9Requested = this.isJ9ExplicitlyRequested || this.isJ9RequestedViaVendor
        val isJ9Vm = metadata.capabilities!!.contains(JavaInstallationCapability.J9_VIRTUAL_MACHINE)
        return j9Requested == isJ9Vm
    }

    private val isJ9ExplicitlyRequested: Boolean
        get() = jvmImplementation == JvmImplementation.Companion.J9

    private val isJ9RequestedViaVendor: Boolean
        get() = vendorSpec !== any() && vendorSpec.test(JvmVendor.KnownJvmVendor.IBM.asJvmVendor())
}
