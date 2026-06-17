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
package org.gradle.api.internal.tasks

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches
import org.gradle.api.internal.tasks.compile.incremental.cache.UserHomeScopedCompileCaches
import org.gradle.api.internal.tasks.compile.incremental.classpath.CachingClassSetAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClassSetAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.classpath.DefaultClassSetAnalyzer
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.initialization.JdkToolsInitializer
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.StreamHasher
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.internal.vfs.FileSystemAccess

class CompileServices : AbstractGradleModuleServices() {
    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildScopeCompileServices())
    }

    public override fun registerGradleUserHomeServices(registration: ServiceRegistration) {
        registration.addProvider(UserHomeScopeServices())
    }

    private class BuildScopeCompileServices : ServiceRegistrationProvider {
        @Provides
        fun configure(registration: ServiceRegistration, initializer: JdkToolsInitializer) {
            // Hackery
            initializer.initializeJdkTools()
        }

        @Provides
        fun createIncrementalCompilerFactory(buildOperationExecutor: BuildOperationExecutor, interner: StringInterner, classSetAnalyzer: ClassSetAnalyzer): IncrementalCompilerFactory {
            return IncrementalCompilerFactory(buildOperationExecutor, interner, classSetAnalyzer)
        }

        @Provides
        fun createClassAnalyzer(interner: StringInterner, cache: GeneralCompileCaches): ClassDependenciesAnalyzer {
            return CachingClassDependenciesAnalyzer(DefaultClassDependenciesAnalyzer(interner), cache.getClassAnalysisCache())
        }

        @Provides
        fun createClassSetAnalyzer(
            fileHasher: FileHasher, streamHasher: StreamHasher, classAnalyzer: ClassDependenciesAnalyzer,
            fileOperations: FileOperations, fileSystemAccess: FileSystemAccess, cache: GeneralCompileCaches
        ): ClassSetAnalyzer {
            return CachingClassSetAnalyzer(
                DefaultClassSetAnalyzer(fileHasher, streamHasher, classAnalyzer, fileOperations),
                fileSystemAccess,
                cache.getClassSetAnalysisCache()
            )
        }
    }

    private class UserHomeScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createCompileCaches(cacheBuilderFactory: GlobalScopedCacheBuilderFactory, inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory, interner: StringInterner): GeneralCompileCaches {
            return UserHomeScopedCompileCaches(cacheBuilderFactory, inMemoryCacheDecoratorFactory, interner)
        }
    }
}
