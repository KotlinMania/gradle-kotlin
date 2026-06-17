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
package org.gradle.nativeplatform.toolchain.internal.clang

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.internal.gcc.AbstractGccCompatibleToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory
import org.gradle.process.internal.ExecActionFactory
import org.jspecify.annotations.NullMarked
import javax.inject.Inject

@NullMarked
class ClangToolChain @Inject constructor(
    name: String,
    buildOperationExecutor: BuildOperationExecutor,
    operatingSystem: OperatingSystem,
    fileResolver: FileResolver,
    execActionFactory: ExecActionFactory,
    compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    metaDataProviderFactory: CompilerMetaDataProviderFactory,
    standardLibraryDiscovery: SystemLibraryDiscovery,
    instantiator: Instantiator,
    workerLeaseService: WorkerLeaseService
) : AbstractGccCompatibleToolChain(
    name,
    buildOperationExecutor,
    operatingSystem,
    fileResolver,
    execActionFactory,
    compilerOutputFileNamingSchemeFactory,
    metaDataProviderFactory.clang(),
    standardLibraryDiscovery,
    instantiator,
    workerLeaseService
), Clang {
    override fun configureDefaultTools(toolChain: DefaultGccPlatformToolChain) {
        toolChain.linker.executable = "clang++"
        toolChain.getcCompiler().executable = "clang"
        toolChain.cppCompiler.executable = "clang++"
        toolChain.objcCompiler.executable = "clang"
        toolChain.objcppCompiler.executable = "clang++"
        toolChain.assembler.executable = "clang"
    }

    val typeName: String
        get() = "Clang"

    companion object {
        const val DEFAULT_NAME: String = "clang"
    }
}
