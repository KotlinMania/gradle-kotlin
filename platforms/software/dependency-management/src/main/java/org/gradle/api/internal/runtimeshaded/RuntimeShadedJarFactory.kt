/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.runtimeshaded

import org.gradle.api.Action
import org.gradle.api.internal.classpath.RuntimeApiInfo
import org.gradle.cache.internal.GeneratedGradleJarCache
import org.gradle.internal.classpath.ClasspathBuilder
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

@ServiceScope(Scope.Build::class)
class RuntimeShadedJarFactory(
    private val cache: GeneratedGradleJarCache,
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val classpathWalker: ClasspathWalker,
    private val classpathBuilder: ClasspathBuilder,
    private val buildOperationRunner: BuildOperationRunner,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val runtimeApiInfo: RuntimeApiInfo
) {
    fun get(type: RuntimeShadedJarType, classpath: MutableCollection<out File?>): File {
        val jarFile = cache.get(type.getIdentifier(), Action { file: File? ->
            buildOperationRunner.run(object : RunnableBuildOperation {
                override fun run(context: BuildOperationContext) {
                    val resource = getPackageListUrl(type)
                    val creator = RuntimeShadedJarCreator(
                        progressLoggerFactory,
                        buildOperationExecutor,
                        ImplementationDependencyRelocator(resource),
                        classpathWalker,
                        classpathBuilder
                    )
                    creator.create(type, file!!, classpath)
                }

                override fun description(): BuildOperationDescriptor.Builder {
                    return@get BuildOperationDescriptor
                        .displayName("Generate " + type.getDisplayName())
                        .progressDisplayName("Generating " + type.getDisplayName())
                }
            })
        })
        LOGGER.debug("Using Gradle runtime shaded JAR file: {}", jarFile)
        return jarFile
    }

    private fun getPackageListUrl(type: RuntimeShadedJarType): URL? {
        when (type) {
            RuntimeShadedJarType.API -> return runtimeApiInfo.getRelocatedApiPackagesResource()
            RuntimeShadedJarType.TEST_KIT -> return RuntimeShadedJarFactory::class.java.getResource(type.getIdentifier() + "-relocated.txt")
        }

        throw IllegalArgumentException("Unsupported runtime shaded jar type: " + type)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(RuntimeShadedJarFactory::class.java)
    }
}
