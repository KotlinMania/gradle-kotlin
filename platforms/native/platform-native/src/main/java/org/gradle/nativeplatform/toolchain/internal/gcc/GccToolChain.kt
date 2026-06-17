/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory
import org.gradle.process.internal.ExecActionFactory
import org.jspecify.annotations.NullMarked
import javax.inject.Inject

/**
 * Compiler adapter for GCC.
 */
@NullMarked
class GccToolChain @Inject constructor(
    instantiator: Instantiator,
    name: String,
    buildOperationExecutor: BuildOperationExecutor,
    operatingSystem: OperatingSystem,
    fileResolver: FileResolver,
    execActionFactory: ExecActionFactory,
    compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    metaDataProviderFactory: CompilerMetaDataProviderFactory,
    standardLibraryDiscovery: SystemLibraryDiscovery,
    workerLeaseService: WorkerLeaseService
) : AbstractGccCompatibleToolChain(
    name,
    buildOperationExecutor,
    operatingSystem,
    fileResolver,
    execActionFactory,
    compilerOutputFileNamingSchemeFactory,
    metaDataProviderFactory.gcc(),
    standardLibraryDiscovery,
    instantiator,
    workerLeaseService
), Gcc {
    val typeName: String
        get() = "GNU GCC"

    override fun initForImplementation(platformToolChain: DefaultGccPlatformToolChain, versionResult: GccMetadata) {
        platformToolChain.setCanUseCommandFile(versionResult.getVersion().getMajor() >= 4)
    }

    companion object {
        const val DEFAULT_NAME: String = "gcc"
    }
}
