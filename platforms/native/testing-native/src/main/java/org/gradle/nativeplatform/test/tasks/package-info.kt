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
 * Tasks for test execution.
 */
package org.gradle.nativeplatform.test.tasks

import org.gradle.internal.logging.ConsoleRenderer.asClickableFileUrl
import org.gradle.internal.file.Chmod.chmod
import org.gradle.nativeplatform.toolchain.internal.xcode.AbstractLocator.find
import org.gradle.process.ExecResult.assertNormalExitValue
import org.gradle.language.internal.NativeComponentFactory.newInstance
import org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine
import org.gradle.language.swift.internal.DefaultSwiftComponent.getBinaries
import org.gradle.language.internal.DefaultBinaryCollection.get
import org.gradle.language.internal.DefaultBinaryCollection.whenElementKnown
import org.gradle.language.nativeplatform.internal.Dimensions.unitTestVariants
import org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector.select
import org.gradle.language.internal.DefaultBinaryCollection.realizeNow
import org.gradle.language.swift.internal.DefaultSwiftBinary.getCompileTask
import org.gradle.language.swift.internal.DefaultSwiftBinary.getSwiftSource
import org.gradle.language.internal.DefaultNativeBinary.getNames
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal.getForPlatform
import org.gradle.language.nativeplatform.internal.Names.getTaskName
import org.gradle.nativeplatform.tasks.AbstractLinkTask.source
import org.gradle.language.internal.DefaultNativeBinary.getObjects
import org.gradle.nativeplatform.tasks.AbstractLinkTask.lib
import org.gradle.language.swift.internal.DefaultSwiftBinary.getLinkLibraries
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal.select
import org.gradle.language.swift.internal.DefaultSwiftBinary.getBaseName
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider.getExecutableName
import org.gradle.nativeplatform.tasks.AbstractLinkTask.getDebuggable
import org.gradle.language.swift.internal.DefaultSwiftBinary.isDebuggable
import org.gradle.language.nativeplatform.ComponentWithInstallation.installTask
import org.gradle.nativeplatform.tasks.InstallExecutable.runScriptFile
import org.gradle.language.swift.internal.DefaultSwiftBinary.getTargetMachine
import org.gradle.language.swift.SwiftComponent.getBinaries
import org.gradle.language.BinaryCollection.whenElementFinalized
import org.gradle.language.swift.ProductionSwiftComponent.getDevelopmentBinary
import org.gradle.language.internal.DefaultNativeBinary.implementationDependencies
import org.gradle.language.nativeplatform.tasks.UnexportMainSymbol.relocatedObjects
import org.gradle.language.swift.internal.DefaultSwiftBinary.linkConfiguration
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.os.OperatingSystem.isMacOsX
import org.gradle.process.internal.ClientExecHandleBuilderFactory.newExecHandleBuilder
import org.gradle.process.internal.ClientExecHandleBuilder.executable
import org.gradle.process.internal.ClientExecHandleBuilder.setWorkingDir
import org.gradle.process.internal.ExecHandle.start
import org.gradle.process.internal.ExecHandle.waitForFinish
import org.gradle.process.ExecResult.rethrowFailure
import org.gradle.internal.io.TextStream.endOfStream
import org.gradle.process.internal.ClientExecHandleBuilder.setArgs
import org.gradle.process.internal.ClientExecHandleBuilder.setStandardOutput
import org.gradle.process.internal.ClientExecHandleBuilder.setErrorOutput
import org.gradle.process.internal.BaseExecHandleBuilder.build
import org.gradle.process.internal.ExecHandle.abort
import org.gradle.language.swift.internal.DefaultSwiftComponent.getNames
import org.gradle.language.nativeplatform.internal.Names.withSuffix
import org.gradle.language.swift.internal.DefaultSwiftComponent.getName
import org.gradle.language.nativeplatform.internal.Names.Companion.of
import org.gradle.language.swift.internal.DefaultSwiftComponent.getSwiftSource
import org.gradle.language.swift.SwiftComponent.implementationDependencies
import org.gradle.language.internal.DefaultBinaryCollection.add
import org.gradle.language.internal.DefaultComponentDependencies.implementationDependencies

