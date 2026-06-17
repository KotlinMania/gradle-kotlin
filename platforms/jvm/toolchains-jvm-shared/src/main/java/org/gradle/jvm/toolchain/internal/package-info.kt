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
/**
 * Implementations and related internal support types for JVM toolchains.
 */
package org.gradle.jvm.toolchain.internal

import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isMacOsX
import org.gradle.internal.jvm.inspection.JvmMetadataDetector.getMetadata
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.autoProvisioned
import org.gradle.internal.jvm.inspection.JavaInstallationCapability.toDisplayName
import org.gradle.internal.RenderingUtils.oxfordJoin
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec.test
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec.Companion.any
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.asJvmVendor
import org.gradle.jvm.toolchain.JavaLanguageVersion.asInt
import org.gradle.internal.os.OperatingSystem.getExecutableName
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withContext
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.jvm.toolchain.JavaLanguageVersion.Companion.of
import org.gradle.api.JavaVersion.majorVersion
import org.gradle.internal.deprecation.DocumentedFailure.builder
import org.gradle.internal.deprecation.DocumentedFailure.Builder.withSummary
import org.gradle.internal.deprecation.DocumentedFailure.Builder.withAdvice
import org.gradle.internal.deprecation.DocumentedFailure.Builder.build
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.autoDetected
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.userDefined
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry.toolchains
import org.gradle.jvm.toolchain.internal.InstallationLocation.isAutoProvisioned
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUserManual
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry.addInstallation

