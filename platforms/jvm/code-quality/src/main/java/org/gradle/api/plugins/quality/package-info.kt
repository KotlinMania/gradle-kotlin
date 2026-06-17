/*
 * Copyright 2010 the original author or authors.
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
 * Plugins which measure and enforce code quality.
 */
package org.gradle.api.plugins.quality

import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.JavaVersion.toString
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.JavaVersion.isJava9Compatible

