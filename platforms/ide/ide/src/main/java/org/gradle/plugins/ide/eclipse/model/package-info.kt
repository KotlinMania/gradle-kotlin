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
 * Classes for the model used by the eclipse plugins.
 */
package org.gradle.plugins.ide.eclipse.model

import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.internal.deprecation.DeprecationLogger.deprecateTask
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.util.internal.CollectionUtils.partition
import org.gradle.util.internal.CollectionUtils.intersection
import org.gradle.util.internal.CollectionUtils.flattenCollections
import org.gradle.util.internal.CollectionUtils.sort
import org.gradle.api.JavaVersion.toString
import org.gradle.api.JavaVersion.majorVersion
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.launcher.cli.internal.HelpRenderer.render
import org.gradle.StartParameter.taskNames
import org.gradle.process.internal.CurrentProcess.jvmOptions
import org.gradle.process.internal.JvmOptions.allImmutableJvmArgs
import org.gradle.launcher.cli.internal.VersionInfoRenderer.render
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory.create
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour
import org.gradle.util.internal.CollectionUtils.collect

