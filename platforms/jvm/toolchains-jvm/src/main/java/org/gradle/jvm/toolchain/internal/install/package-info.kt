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
/**
 * Types for installing JVM toolchains.
 */
package org.gradle.jvm.toolchain.internal.install

import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isMacOsX
import org.gradle.api.internal.StartParameterInternal.projectPropertiesUntracked
import org.gradle.api.internal.StartParameterInternal.isDaemonJvmCriteriaConfigured
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.logging.text.StyledTextOutputFactory.create
import org.gradle.internal.logging.text.StyledTextOutput.println
import org.gradle.internal.logging.text.StyledTextOutput.withStyle
import org.gradle.internal.logging.text.StyledTextOutput.format
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry.toolchains
import org.gradle.platform.internal.CurrentBuildPlatform.toBuildPlatform

