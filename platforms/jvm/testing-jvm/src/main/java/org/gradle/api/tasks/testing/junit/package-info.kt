/*
 * Copyright 2009 the original author or authors.
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
 * JUnit specific testing classes.
 */
package org.gradle.api.tasks.testing.junit

import org.gradle.internal.serialization.Cached.get
import org.gradle.process.internal.JavaForkOptionsFactory.newDecoratedJavaForkOptions
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion.asInt
import org.gradle.process.internal.JavaForkOptionsFactory.newJavaForkOptions
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.JavaVersion.isCompatibleWith
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.StartParameter.maxWorkerCount
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.Cast.cast
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory.create
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import org.gradle.api.logging.Logger.info
import org.gradle.util.internal.CollectionUtils.flattenCollections

