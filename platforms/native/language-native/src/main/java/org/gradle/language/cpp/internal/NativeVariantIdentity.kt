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
package org.gradle.language.cpp.internal

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.component.ComponentWithCoordinates
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.provider.Provider
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.TargetMachine
import org.gradle.util.internal.GUtil

class NativeVariantIdentity @JvmOverloads constructor(
    private val name: String?,
    private val baseName: Provider<String?>,
    private val group: Provider<String?>,
    private val version: Provider<String?>,
    val isDebuggable: Boolean,
    val isOptimized: Boolean,
    @JvmField val targetMachine: TargetMachine?,
    linkVariant: UsageContext?,
    runtimeVariant: UsageContext?,
    linkage: Linkage? = null
) : SoftwareComponentInternal, ComponentWithCoordinates {
    val linkVariant: SoftwareComponentVariant?
    val runtimeVariant: SoftwareComponentVariant?
    val linkage: Linkage?
    private val variants: MutableSet<UsageContext?>

    init {
        this.linkVariant = linkVariant
        this.runtimeVariant = runtimeVariant
        this.linkage = linkage
        this.variants = LinkedHashSet<UsageContext?>()
        if (linkVariant != null) {
            variants.add(linkVariant)
        }
        if (runtimeVariant != null) {
            variants.add(runtimeVariant)
        }
    }

    override fun getCoordinates(): ModuleVersionIdentifier {
        return DefaultModuleVersionIdentifier.newId(group.get()!!, baseName.get() + "_" + GUtil.toWords(name, '_'), version.get()!!)
    }

    override fun getUsages(): MutableSet<out UsageContext?> {
        return variants
    }

    override fun getName(): String? {
        return name
    }
}
