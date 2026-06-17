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
package org.gradle.jvm.toolchain

import com.google.common.base.MoreObjects
import org.gradle.api.Describable
import org.gradle.api.Incubating
import org.gradle.api.Transformer
import org.gradle.internal.HasInternalProtocol

/**
 * Requirements for selecting a Java toolchain.
 *
 *
 * A toolchain is a JRE/JDK used by the tasks of a build.
 * Tasks may require one or more of the tools (javac, java, or javadoc) of a toolchain.
 * Depending on the needs of a build, only toolchains matching specific characteristics can be used to run a build or a specific task of a build.
 *
 *
 * Even though specification properties can be configured independently,
 * the configuration must follow certain rules in order to form a  specification.
 *
 *
 * A `JavaToolchainSpec` is considered *valid* in two cases:
 *
 *  *  when no properties have been set, i.e. the specification is *empty*;
 *  *  when [language version][.getLanguageVersion] has been set, optionally followed by setting any other property.
 *
 *
 *
 * In other words, if a vendor or an implementation are specified, they must be accompanied by the language version.
 * An empty specification in most cases corresponds to the toolchain that runs the current build.
 *
 *
 * Usage of *invalid* instances of `JavaToolchainSpec` is deprecated and will be removed in the future versions of Gradle.
 *
 * @since 6.7
 */
@HasInternalProtocol
interface JavaToolchainSpec : Describable {
    /**
     * The exact version of the Java language that the toolchain is required to support.
     */
    @JvmField
    val languageVersion: Property<JavaLanguageVersion>?

    /**
     * The vendor of the toolchain.
     *
     *
     * By default, toolchains from any vendor are eligible.
     *
     *
     * Note that the vendor can only be configured if the [language version][.getLanguageVersion] is configured as well.
     *
     * @since 6.8
     */
    @JvmField
    val vendor: Property<JvmVendorSpec>?

    /**
     * The virtual machine implementation of the toolchain.
     *
     *
     * By default, any implementation (hotspot, j9, ...) is eligible.
     *
     *
     * Note that the implementation can only be configured if the [language version][.getLanguageVersion] is configured as well.
     *
     * @since 6.8
     */
    val implementation: Property<JvmImplementation>?

    @JvmField
    @get:Incubating
    val nativeImageCapable: Property<Boolean>?

    override fun getDisplayName(): String {
        val builder = MoreObjects.toStringHelper("")
        builder.omitNullValues()
        builder.add("languageVersion", this.languageVersion.map<String>(Transformer { obj: JavaLanguageVersion? -> obj.toString() }).getOrElse("unspecified"))
        builder.add("vendor", this.vendor.map<String>(Transformer { obj: JvmVendorSpec? -> obj.toString() }).getOrNull())
        builder.add("implementation", this.implementation.map<String>(Transformer { obj: JvmImplementation? -> obj.toString() }).getOrNull())
        builder.add("nativeImageCapable", this.nativeImageCapable.getOrElse(false))
        return builder.toString()
    }
}
