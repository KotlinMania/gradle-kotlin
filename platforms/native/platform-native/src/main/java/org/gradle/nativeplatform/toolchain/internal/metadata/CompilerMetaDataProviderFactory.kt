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
package org.gradle.nativeplatform.toolchain.internal.metadata

import org.gradle.api.Action
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadataProvider
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.internal.ExecActionFactory
import java.io.File

@ServiceScope(Scope.Build::class)
class CompilerMetaDataProviderFactory(execActionFactory: ExecActionFactory?) {
    private val gcc: CachingCompilerMetaDataProvider<GccMetadata?>
    private val clang: CachingCompilerMetaDataProvider<GccMetadata?>
    private val swiftc: CachingCompilerMetaDataProvider<SwiftcMetadata?>

    init {
        gcc = CachingCompilerMetaDataProvider<GccMetadata?>(GccMetadataProvider.Companion.forGcc(execActionFactory))
        clang = CachingCompilerMetaDataProvider<GccMetadata?>(GccMetadataProvider.Companion.forClang(execActionFactory))
        swiftc = CachingCompilerMetaDataProvider<SwiftcMetadata?>(SwiftcMetadataProvider(execActionFactory))
    }

    fun gcc(): CompilerMetaDataProvider<GccMetadata?> {
        return gcc
    }

    fun clang(): CompilerMetaDataProvider<GccMetadata?> {
        return clang
    }

    fun swiftc(): CompilerMetaDataProvider<SwiftcMetadata?> {
        return swiftc
    }

    private class CachingCompilerMetaDataProvider<T : CompilerMetadata?>(private val delegate: CompilerMetaDataProvider<T?>) : CompilerMetaDataProvider<T?> {
        private val resultMap: MutableMap<Key?, SearchResult<T?>?> = HashMap<Key?, SearchResult<T?>?>()

        override fun getCompilerMetaData(path: MutableList<File?>, configureAction: Action<in CompilerMetaDataProvider.CompilerExecSpec?>): SearchResult<T?>? {
            val execSpec = AbstractMetadataProvider.DefaultCompilerExecSpec()
            configureAction.execute(execSpec)

            val key = Key(execSpec.executable, execSpec.args, path, execSpec.environments)
            var result = resultMap.get(key)
            if (result == null) {
                result = delegate.getCompilerMetaData(path, configureAction)
                resultMap.put(key, result)
            }
            return result
        }

        override fun getCompilerType(): CompilerType? {
            return delegate.getCompilerType()
        }
    }

    private class Key(val gccBinary: File, val args: MutableList<String?>, val path: MutableList<File?>, private val environmentVariables: MutableMap<String?, *>) {
        override fun equals(obj: Any?): Boolean {
            if (obj !is Key) {
                return false
            }
            val other = obj
            return other.gccBinary == gccBinary && other.args == args && other.path == path && other.environmentVariables == environmentVariables
        }

        override fun hashCode(): Int {
            return gccBinary.hashCode() xor args.hashCode() xor path.hashCode() xor environmentVariables.hashCode()
        }
    }
}
