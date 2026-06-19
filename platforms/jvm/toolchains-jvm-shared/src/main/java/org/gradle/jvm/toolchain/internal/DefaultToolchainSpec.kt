/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec.Companion.any
import java.util.Objects
import javax.inject.Inject

open class DefaultToolchainSpec @Inject constructor(propertyFactory: PropertyFactory) : JavaToolchainSpecInternal {
    private val version: Property<JavaLanguageVersion> = propertyFactory.property<JavaLanguageVersion>(JavaLanguageVersion::class.java)
    final override val vendor: Property<JvmVendorSpec> = propertyFactory.property<JvmVendorSpec>(JvmVendorSpec::class.java)
    final override val implementation: Property<JvmImplementation> = propertyFactory.property<JvmImplementation>(JvmImplementation::class.java)
    final override val nativeImageCapable: Property<Boolean> = propertyFactory.property<Boolean>(Boolean::class.java)

    class Key(languageVersion: JavaLanguageVersion?, vendor: JvmVendorSpec?, implementation: JvmImplementation?, nativeImageCapable: Boolean) : JavaToolchainSpecInternal.Key {
        private val languageVersion: JavaLanguageVersion?
        private val vendor: JvmVendorSpec?
        private val implementation: JvmImplementation?
        private val nativeImageCapable: Boolean

        init {
            this.languageVersion = languageVersion
            this.vendor = vendor
            this.implementation = implementation
            this.nativeImageCapable = nativeImageCapable
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as Key
            return languageVersion == that.languageVersion
                    && vendor == that.vendor
                    && implementation == that.implementation
                    && nativeImageCapable == that.nativeImageCapable
        }

        override fun hashCode(): Int {
            return Objects.hash(languageVersion, vendor, implementation, nativeImageCapable)
        }

        override fun toString(): String {
            return "DefaultKey{" +
                    "languageVersion=" + languageVersion +
                    ", vendor=" + vendor +
                    ", implementation=" + implementation +
                    ", nativeImageCapable=" + nativeImageCapable +
                    '}'
        }
    }

    init {
        vendor.convention(conventionVendor)
        implementation.convention(conventionImplementation)
    }

    final override val languageVersion: Property<JavaLanguageVersion>
        get() = version

    override fun toKey(): JavaToolchainSpecInternal.Key? {
        return DefaultToolchainSpec.Key(languageVersion.getOrNull(), vendor.getOrNull(), implementation.getOrNull(), nativeImageCapable.getOrElse(false))
    }

    override val isConfigured: Boolean
        get() = languageVersion.isPresent()

    @Suppress("deprecation")
    override val isValid: Boolean
        get() = (languageVersion.isPresent() || this.isSecondaryPropertiesUnchanged) && languageVersion.getOrNull() !== DefaultJavaLanguageVersion.UNKNOWN

    private val isSecondaryPropertiesUnchanged: Boolean
        get() = conventionVendor == vendor.getOrNull() &&
                conventionImplementation == implementation.getOrNull()

    override fun toString(): String {
        return getDisplayName()
    }

    companion object {
        private val conventionVendor: JvmVendorSpec
            get() = any()

        private val conventionImplementation: JvmImplementation
            get() = JvmImplementation.Companion.VENDOR_SPECIFIC
    }
}
