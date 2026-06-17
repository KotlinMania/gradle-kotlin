/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.api.annotations

/**
 * A configuration for deprecation of a replaced property/accessor.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class ReplacedDeprecation(
    /**
     * Set if deprecation nagging is enabled or not
     */
    val enabled: Boolean = true,
    /**
     * Corresponds to .willBecomeAnErrorInGradle10() in the DeprecationLogger
     */
    val removedIn: RemovedIn = RemovedIn.UNSPECIFIED,
    /**
     * Corresponds to .withUpgradeGuideSection(majorVersion, section) in the DeprecationLogger
     */
    val withUpgradeGuideMajorVersion: Int = -1,
    /**
     * Corresponds to .withUpgradeGuideSection(majorVersion, section) in the DeprecationLogger
     *
     * withUpgradeGuideSection is only added if withUpgradeGuideMajorVersion is set
     */
    val withUpgradeGuideSection: String = "",
    /**
     * Corresponds to .withDslReference() in the DeprecationLogger
     *
     * WithDslReference has lower priority than withUpgradeGuideSection
     */
    val withDslReference: Boolean = false
) {
    enum class RemovedIn {
        // For properties removed in Gradle 9
        GRADLE9,

        // For properties that were changed to lazy
        UNSPECIFIED
    }
}
