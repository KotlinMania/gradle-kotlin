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
package org.gradle.language.nativeplatform.internal

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.component.DefaultSoftwareComponentVariant
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.Dimensions.createDimensionSuffix
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.lang.String
import java.util.Arrays
import java.util.stream.Collectors
import kotlin.Boolean
import kotlin.require

object Dimensions {
    fun createDimensionSuffix(dimensionValue: Named, multivalueProperty: MutableCollection<*>): String {
        return createDimensionSuffix(dimensionValue.getName(), multivalueProperty)
    }

    fun createDimensionSuffix(dimensionValue: String, multivalueProperty: MutableCollection<*>): String {
        if (isDimensionVisible(multivalueProperty)) {
            return StringUtils.capitalize(dimensionValue.lowercase())
        }
        return ""
    }

    private fun isDimensionVisible(multivalueProperty: MutableCollection<*>): Boolean {
        return multivalueProperty.size > 1
    }

    fun unitTestVariants(
        baseName: Provider<String?>?, declaredTargetMachines: SetProperty<TargetMachine?>, declaredTargetMachinesOfTestedComponent: SetProperty<TargetMachine?>?,
        attributesFactory: AttributesFactory,
        group: Provider<String?>?, version: Provider<String?>?,
        action: Action<NativeVariantIdentity?>
    ) {
        val targetMachines = extractAndValidate<TargetMachine?>("target machine", "unit test", declaredTargetMachines)
        if (declaredTargetMachinesOfTestedComponent != null) {
            val targetMachinesOfTestedComponent = extractAndValidate<TargetMachine?>("target machine", "component under test", declaredTargetMachinesOfTestedComponent)
            validateTargetMachines(targetMachines, targetMachinesOfTestedComponent)
        }
        variants(baseName, Arrays.asList<BuildType?>(BuildType.Companion.DEBUG), targetMachines, attributesFactory, group, version, action)
    }

    fun applicationVariants(
        baseName: Provider<String?>?, declaredTargetMachines: SetProperty<TargetMachine?>,
        attributesFactory: AttributesFactory,
        group: Provider<String?>?, version: Provider<String?>?,
        action: Action<NativeVariantIdentity?>
    ) {
        val buildTypes: MutableCollection<BuildType> = BuildType.Companion.DEFAULT_BUILD_TYPES
        val targetMachines = extractAndValidate<TargetMachine?>("target machine", "application", declaredTargetMachines)
        variants(baseName, buildTypes, targetMachines, attributesFactory, group, version, action)
    }

    fun libraryVariants(
        baseName: Provider<String?>?, declaredLinkages: SetProperty<Linkage?>, declaredTargetMachines: SetProperty<TargetMachine?>,
        attributesFactory: AttributesFactory,
        group: Provider<String?>?, version: Provider<String?>?,
        action: Action<NativeVariantIdentity?>
    ) {
        val buildTypes: MutableCollection<BuildType> = BuildType.Companion.DEFAULT_BUILD_TYPES
        val linkages = extractAndValidate<Linkage?>("linkage", "library", declaredLinkages)
        val targetMachines = extractAndValidate<TargetMachine?>("target machine", "library", declaredTargetMachines)
        variants(baseName, buildTypes, linkages, targetMachines, attributesFactory, group, version, action)
    }

    private fun <T> extractAndValidate(propertyName: String?, componentName: String?, declared: SetProperty<T?>): MutableCollection<T?> {
        declared.finalizeValue()
        val value: MutableCollection<T?> = declared.get()
        assertNonEmpty(propertyName, componentName, value)
        return value
    }

    private fun assertNonEmpty(propertyName: String?, componentName: String?, property: MutableCollection<*>) {
        require(!property.isEmpty()) { String.format("A %s needs to be specified for the %s.", propertyName, componentName) }
    }

    private fun validateTargetMachines(testTargetMachines: MutableCollection<TargetMachine>, mainTargetMachines: MutableCollection<TargetMachine?>) {
        for (machine in testTargetMachines) {
            require(mainTargetMachines.contains(machine)) { "The target machine " + machine.toString() + " was specified for the unit test, but this target machine was not specified on the component under test." }
        }
    }

    private fun variants(
        baseName: Provider<String?>?, buildTypes: MutableCollection<BuildType>, linkages: MutableCollection<Linkage>, targetMachines: MutableCollection<TargetMachine>,
        attributesFactory: AttributesFactory,  // TODO: These should come from somewhere else, probably
        group: Provider<String?>?, version: Provider<String?>?,
        action: Action<NativeVariantIdentity?>
    ) {
        for (buildType in buildTypes) {
            for (linkage in linkages) {
                for (targetMachine in targetMachines) {
                    val variantNameToken: MutableList<String?> = ArrayList<String?>()
                    // FIXME: Always build type name to keep parity with previous Gradle version in tooling API
                    variantNameToken.add(buildType.getName())
                    variantNameToken.add(createDimensionSuffix(linkage, linkages))
                    variantNameToken.add(createDimensionSuffix(targetMachine.operatingSystemFamily, targetMachinesToOperatingSystems(targetMachines)))
                    variantNameToken.add(createDimensionSuffix(targetMachine.architecture, targetMachinesToArchitectures(targetMachines)))

                    val variantName = StringUtils.uncapitalize(String.join("", variantNameToken))

                    val runtimeAttributes: AttributeContainer = attributesFactory.mutable()
                    runtimeAttributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, runtimeAttributes.named<Usage?>(Usage::class.java, Usage.NATIVE_RUNTIME))
                    addCommonAttributes(buildType, targetMachine, runtimeAttributes)
                    runtimeAttributes.attribute<Linkage?>(CppBinary.Companion.LINKAGE_ATTRIBUTE, linkage)

                    val runtimeVariant: UsageContext = DefaultSoftwareComponentVariant(variantName + "Runtime", runtimeAttributes)

                    val linkAttributes: AttributeContainer = attributesFactory.mutable()
                    linkAttributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, linkAttributes.named<Usage?>(Usage::class.java, Usage.NATIVE_LINK))
                    addCommonAttributes(buildType, targetMachine, linkAttributes)
                    linkAttributes.attribute<Linkage?>(CppBinary.Companion.LINKAGE_ATTRIBUTE, linkage)
                    val linkVariant: UsageContext = DefaultSoftwareComponentVariant(variantName + "Link", linkAttributes)

                    val variantIdentity =
                        NativeVariantIdentity(variantName, baseName, group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine, linkVariant, runtimeVariant, linkage)

                    action.execute(variantIdentity)
                }
            }
        }
    }

    private fun variants(
        baseName: Provider<kotlin.String?>?, buildTypes: MutableCollection<BuildType>, targetMachines: MutableCollection<TargetMachine>,
        attributesFactory: AttributesFactory,  // TODO: These should come from somewhere else, probably
        group: Provider<kotlin.String?>?, version: Provider<kotlin.String?>?,
        action: Action<NativeVariantIdentity?>
    ) {
        for (buildType in buildTypes) {
            for (targetMachine in targetMachines) {
                val variantNameToken: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
                // FIXME: Always build type name to keep parity with previous Gradle version in tooling API
                variantNameToken.add(buildType.getName())
                variantNameToken.add(createDimensionSuffix(targetMachine.operatingSystemFamily, targetMachinesToOperatingSystems(targetMachines)))
                variantNameToken.add(createDimensionSuffix(targetMachine.architecture, targetMachinesToArchitectures(targetMachines)))

                val variantName = StringUtils.uncapitalize(String.join("", variantNameToken))

                val runtimeAttributes: AttributeContainer = attributesFactory.mutable()
                runtimeAttributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, runtimeAttributes.named<Usage?>(Usage::class.java, Usage.NATIVE_RUNTIME))
                addCommonAttributes(buildType, targetMachine, runtimeAttributes)

                val runtimeVariant: UsageContext = DefaultSoftwareComponentVariant(variantName + "Runtime", runtimeAttributes)

                val linkVariant: UsageContext? = null

                val variantIdentity = NativeVariantIdentity(variantName, baseName, group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine, linkVariant, runtimeVariant, null)

                action.execute(variantIdentity)
            }
        }
    }

    private fun targetMachinesToOperatingSystems(targetMachines: MutableCollection<TargetMachine>): MutableSet<OperatingSystemFamily> {
        return targetMachines.stream().map<OperatingSystemFamily?> { obj: TargetMachine? -> obj!!.operatingSystemFamily }.collect(Collectors.toSet())
    }

    private fun targetMachinesToArchitectures(targetMachines: MutableCollection<TargetMachine>): MutableSet<MachineArchitecture> {
        return targetMachines.stream().map<MachineArchitecture?> { obj: TargetMachine? -> obj!!.architecture }.collect(Collectors.toSet())
    }

    private fun addCommonAttributes(buildType: BuildType, targetMachine: TargetMachine, runtimeAttributes: AttributeContainer) {
        runtimeAttributes.attribute<Boolean?>(CppBinary.Companion.DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable())
        runtimeAttributes.attribute<Boolean?>(CppBinary.Companion.OPTIMIZED_ATTRIBUTE, buildType.isOptimized())
        runtimeAttributes.attribute<MachineArchitecture?>(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, targetMachine.architecture)
        runtimeAttributes.attribute<OperatingSystemFamily?>(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, targetMachine.operatingSystemFamily)
    }

    /**
     * Used by all native plugins to work around the missing default feature on Property
     *
     * See https://github.com/gradle/gradle-native/issues/918
     *
     * @since 5.1
     */
    @JvmStatic
    fun useHostAsDefaultTargetMachine(targetMachineFactory: TargetMachineFactory): MutableSet<TargetMachine?> {
        return mutableSetOf<TargetMachine?>((targetMachineFactory as DefaultTargetMachineFactory).host())
    }

    @JvmStatic
    fun tryToBuildOnHost(identity: NativeVariantIdentity): Boolean {
        return DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName() == identity.getTargetMachine().getOperatingSystemFamily().getName()
    }
}
