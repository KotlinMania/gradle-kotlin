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
    private val version: Property<JavaLanguageVersion?>
    private val vendor: Property<JvmVendorSpec?>
    private val implementation: Property<JvmImplementation?>
    private val nativeImageCapable: Property<Boolean?>

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
        version = propertyFactory.property<JavaLanguageVersion?>(JavaLanguageVersion::class.java)
        vendor = propertyFactory.property<JvmVendorSpec?>(JvmVendorSpec::class.java)
        implementation = propertyFactory.property<JvmImplementation?>(JvmImplementation::class.java)
        nativeImageCapable = propertyFactory.property<Boolean?>(Boolean::class.java)

        getVendor().convention(conventionVendor)
        getImplementation().convention(conventionImplementation)
    }

    override fun getLanguageVersion(): Property<JavaLanguageVersion?> {
        return version
    }

    override fun getVendor(): Property<JvmVendorSpec?> {
        return vendor
    }

    override fun getImplementation(): Property<JvmImplementation?> {
        return implementation
    }

    override fun getNativeImageCapable(): Property<Boolean?> {
        return nativeImageCapable
    }

    override fun toKey(): JavaToolchainSpecInternal.Key? {
        return DefaultToolchainSpec.Key(getLanguageVersion().getOrNull(), getVendor().getOrNull(), getImplementation().getOrNull(), nativeImageCapable.getOrElse(false)!!)
    }

    override fun isConfigured(): Boolean {
        return getLanguageVersion().isPresent()
    }

    @Suppress("deprecation")
    override fun isValid(): Boolean {
        return (getLanguageVersion().isPresent() || this.isSecondaryPropertiesUnchanged) && getLanguageVersion().getOrNull() !== DefaultJavaLanguageVersion.UNKNOWN
    }

    private val isSecondaryPropertiesUnchanged: Boolean
        get() = conventionVendor == getVendor().getOrNull() &&
                conventionImplementation == getImplementation().getOrNull()

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
