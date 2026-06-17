/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal

import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.EffectiveClassPath
import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.UnknownModuleException
import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.CacheFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.instrumentation.agent.AgentStatus.Companion.disabled
import org.gradle.internal.logging.LoggingManagerFactory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.NoOpBuildOperationProgressEventEmitter
import org.gradle.internal.service.Provides
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.internal.time.Clock

class TestGlobalScopeServices : GlobalScopeServices(false, disabled(), CurrentGradleInstallation.locate()) {
    @Provides
    override fun createCacheFactory(fileLockManager: FileLockManager, executorFactory: ExecutorFactory, buildOperationRunner: BuildOperationRunner): CacheFactory {
        return TestInMemoryCacheFactory()
    }

    @Provides
    override fun createModuleRegistry(currentGradleInstallation: CurrentGradleInstallation): ModuleRegistry {
        val installation = currentGradleInstallation.getInstallation()
        if (installation == null) {
            // This ProjectBuilder test is being executed from outside a Gradle distribution.
            val classpath = DefaultClassPath.of(EffectiveClassPath(javaClass.getClassLoader()).getAsFiles())
            return MockModuleRegistry(classpath)
        } else {
            return DefaultModuleRegistry(installation)
        }
    }

    @Provides
    fun createLoggingManager(loggingManagerFactory: LoggingManagerFactory): LoggingManagerInternal {
        return loggingManagerFactory.createLoggingManager()!!
    }

    @Provides
    override fun createBuildOperationProgressEventEmitter(
        clock: Clock,
        currentBuildOperationRef: CurrentBuildOperationRef,
        listenerManager: BuildOperationListenerManager
    ): BuildOperationProgressEventEmitter {
        return NoOpBuildOperationProgressEventEmitter()
    }

    /**
     * A module registry backed by a classpath. Each module returned by this
     * registry has the same classpath, equal to the given backing classpath.
     *
     *
     * This is to be used in testing scenarios where it is assumed all classes necessary for Gradle
     * to function are present on the classpath already.
     */
    private class MockModuleRegistry(private val classpath: ClassPath) : ModuleRegistry, Module {
        @Throws(UnknownModuleException::class)
        override fun findModule(name: String): Module? {
            return this
        }

        @Throws(UnknownModuleException::class)
        override fun getModule(name: String): Module {
            return this
        }

        override fun getName(): String {
            return "test"
        }

        override fun getDependencyNames(): MutableList<String> {
            return mutableListOf<String>()
        }

        override fun getImplementationClasspath(): ClassPath {
            return classpath
        }

        override fun getAlias(): Module.ModuleAlias? {
            return null
        }
    }
}
