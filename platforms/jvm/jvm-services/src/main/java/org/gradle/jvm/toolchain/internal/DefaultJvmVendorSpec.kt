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
package org.gradle.jvm.toolchain.internal

import com.google.common.base.Objects
import org.apache.commons.lang3.Strings
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import java.util.function.Predicate

class DefaultJvmVendorSpec : JvmVendorSpec, Predicate<JvmInstallationMetadata?>, Serializable {
    private val match: String?
    private val matchingVendor: JvmVendor.KnownJvmVendor?

    @Transient
    private var matcher: Predicate<JvmVendor?>
    private val description: String?

    // Creates a spec matching a vendor based on a substring of the vendor string
    private constructor(match: String?, description: String?) {
        this.match = match
        this.matchingVendor = null
        this.matcher = Predicate { vendor: JvmVendor? -> Strings.CI.contains(vendor!!.getRawVendor(), match) }
        this.description = description
    }

    // Creates a spec matching a known vendor
    private constructor(knownVendor: JvmVendor.KnownJvmVendor?, description: String?) {
        this.match = null
        this.matchingVendor = knownVendor
        this.matcher = Predicate { vendor: JvmVendor? -> vendor!!.getKnownVendor() == matchingVendor }
        this.description = description
    }

    // Creates a spec matching any vendor
    private constructor() {
        this.match = null
        this.matchingVendor = null
        this.matcher = Predicate { v: JvmVendor? -> true }
        this.description = "any vendor"
    }

    fun toCriteria(): String {
        if (match != null) {
            return match
        } else if (matchingVendor != null) {
            return matchingVendor.asJvmVendor().getKnownVendor().name
        }
        throw IllegalStateException("No matching vendor was specified")
    }

    override fun test(metadata: JvmInstallationMetadata): Boolean {
        val vendor = metadata.getVendor()
        return test(vendor)
    }

    fun test(vendor: JvmVendor?): Boolean {
        return matcher.test(vendor)
    }

    override fun matches(vendor: String): Boolean {
        return test(JvmVendor.Companion.fromString(vendor))
    }

    override fun toString(): String {
        return description!!
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultJvmVendorSpec
        return Objects.equal(description, that.description)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(description)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(`in`: ObjectInputStream) {
        `in`.defaultReadObject()
        if (match != null) {
            matcher = Predicate { vendor: JvmVendor? -> Strings.CI.contains(vendor!!.getRawVendor(), match) }
        } else if (matchingVendor != null) {
            matcher = Predicate { vendor: JvmVendor? -> vendor!!.getKnownVendor() == matchingVendor }
        } else {
            matcher = Predicate { vendor: JvmVendor? -> true }
        }
    }

    companion object {
        private val ANY: JvmVendorSpec = DefaultJvmVendorSpec()

        fun matching(match: String?): JvmVendorSpec {
            return DefaultJvmVendorSpec(match, "vendor matching('" + match + "')")
        }

        fun of(knownVendor: JvmVendor.KnownJvmVendor): JvmVendorSpec {
            return DefaultJvmVendorSpec(knownVendor, knownVendor.asJvmVendor().getDisplayName())
        }

        @JvmStatic
        fun any(): JvmVendorSpec {
            return ANY
        }
    }
}
