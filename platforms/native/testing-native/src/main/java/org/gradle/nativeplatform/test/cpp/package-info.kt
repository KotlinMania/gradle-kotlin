/*
 * Copyright 2017 the original author or authors.
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
 * API classes for C++ test integration.
 */
package org.gradle.nativeplatform.test.cpp

import org.gradle.language.internal.NativeComponentFactory.newInstance
import org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine
import org.gradle.language.cpp.internal.DefaultCppBinary.nativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.Companion.currentArchitecture
import org.gradle.language.cpp.internal.DefaultCppComponent.getBinaries
import org.gradle.language.internal.DefaultBinaryCollection.get
import org.gradle.language.internal.DefaultBinaryCollection.whenElementKnown
import org.gradle.language.internal.DefaultNativeBinary.getNames
import org.gradle.language.nativeplatform.internal.Names.getTaskName
import org.gradle.language.nativeplatform.ComponentWithInstallation.installTask
import org.gradle.language.nativeplatform.ComponentWithInstallation.installDirectory
import org.gradle.nativeplatform.tasks.InstallExecutable.runScriptFile
import org.gradle.nativeplatform.test.tasks.RunTestExecutable.outputDir
import org.gradle.language.nativeplatform.internal.Dimensions.unitTestVariants
import org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector.select
import org.gradle.language.cpp.internal.NativeVariantIdentity.getName
import org.gradle.language.internal.DefaultBinaryCollection.realizeNow
import org.gradle.language.cpp.CppComponent.getBinaries
import org.gradle.language.BinaryCollection.whenElementFinalized
import org.gradle.language.internal.DefaultNativeBinary.implementationDependencies
import org.gradle.language.nativeplatform.tasks.UnexportMainSymbol.relocatedObjects
import org.gradle.language.cpp.internal.DefaultCppBinary.linkConfiguration
import org.gradle.language.cpp.internal.DefaultCppBinary.getTargetMachine
import org.gradle.language.cpp.ProductionCppComponent.getDevelopmentBinary
import org.gradle.language.cpp.internal.DefaultCppComponent.getNames
import org.gradle.language.nativeplatform.internal.Names.withSuffix
import org.gradle.language.nativeplatform.internal.Names.Companion.of
import org.gradle.language.cpp.internal.DefaultCppComponent.getName
import org.gradle.language.cpp.internal.DefaultCppComponent.getCppSource
import org.gradle.language.cpp.internal.DefaultCppComponent.getPrivateHeaderDirs
import org.gradle.language.cpp.CppComponent.implementationDependencies
import org.gradle.language.internal.DefaultBinaryCollection.add
import org.gradle.language.internal.DefaultComponentDependencies.implementationDependencies
import org.gradle.language.cpp.internal.DefaultCppBinary.getCompileIncludePath
import org.gradle.language.cpp.internal.DefaultCppComponent.allHeaderDirs
import org.gradle.nativeplatform.test.TestSuiteComponent.testBinary

