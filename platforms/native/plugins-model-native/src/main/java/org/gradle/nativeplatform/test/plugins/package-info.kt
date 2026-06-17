/*
 * Copyright 2013 the original author or authors.
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
 * Plugin classes for generic support for testing native binaries.
 */
package org.gradle.nativeplatform.test.plugins

import org.gradle.internal.Cast.uncheckedCast
import org.gradle.language.nativeplatform.DependentSourceSet.lib
import org.gradle.nativeplatform.tasks.InstallExecutable.runScriptFile
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.resolve.ProjectModelResolver.resolveProjectModel
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.nativeplatform.toolchain.internal.PCHUtils.generatePrefixHeaderFile
import org.gradle.internal.Cast.cast
import org.gradle.platform.base.internal.PlatformResolvers.register
import org.gradle.language.base.internal.LanguageSourceSetInternal.generatorTask
import org.gradle.language.base.internal.registry.LanguageTransform.sourceSetType
import org.gradle.language.base.internal.SourceTransformTaskConfig.taskPrefix
import org.gradle.language.base.internal.SourceTransformTaskConfig.taskType
import org.gradle.language.base.internal.SourceTransformTaskConfig.configureTask
import org.gradle.nativeplatform.tasks.AbstractLinkTask.lib
import org.gradle.platform.base.Platform.getName
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.nativeplatform.platform.internal.NativePlatforms.defaultPlatformDefinitions
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.language.base.internal.ProjectLayout.buildDir
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal.select
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider.getExecutableName
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider.getSharedLibraryLinkFileName
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider.getSharedLibraryName
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider.getStaticLibraryName
import org.gradle.platform.base.ToolChainRegistry.getForPlatform
import org.gradle.platform.base.internal.DefaultPlatformRequirement.Companion.create
import org.gradle.nativeplatform.platform.internal.NativePlatforms.defaultPlatformName
import org.gradle.platform.base.internal.PlatformResolvers.resolve
import org.gradle.nativeplatform.tasks.InstallExecutable.lib
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary.source
import org.gradle.api.reporting.dependents.internal.DependentComponentsUtils.getBuildScopedTerseName
import org.gradle.internal.logging.text.StyledTextOutput.withStyle
import org.gradle.internal.logging.text.StyledTextOutput.text

