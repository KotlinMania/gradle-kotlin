/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.launcher.bootstrap

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.installation.CurrentGradleInstallation
import kotlin.system.exitProcess

object ProcessBootstrap {
    /**
     * Sets up the ClassLoader structure for the given class, creates an instance and invokes the main `run` method on it.
     *
     * @param moduleName the name of the Gradle module to use for the main class implementation
     */
    fun run(bootstrapName: String?, moduleName: String?, mainClassName: String?, args: Array<String>?) {
        try {
            runNoExit(bootstrapName, moduleName, mainClassName, args)
            exitProcess(0)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            exitProcess(1)
        }
    }

    @Throws(Exception::class)
    private fun runNoExit(bootstrapName: String?, moduleName: String?, mainClassName: String?, args: Array<String>?) {
        val runtimeClassLoader: ClassLoader?
        val antClassLoader: ClassLoader?

        try {
            val moduleRegistry = DefaultModuleRegistry(CurrentGradleInstallation.get())
            val classPathRegistry: ClassPathRegistry = DefaultClassPathRegistry(DefaultClassPathProvider(moduleRegistry))
            val classLoaderFactory: ClassLoaderFactory = DefaultClassLoaderFactory()
            val antClasspath = classPathRegistry.getClassPath("ANT")
            val runtimeClasspath = moduleRegistry.getRuntimeClasspath(moduleName)
            antClassLoader = classLoaderFactory.createIsolatedClassLoader("ant-loader", antClasspath)
            runtimeClassLoader = VisitableURLClassLoader.fromClassPath("ant-and-gradle-loader", antClassLoader, runtimeClasspath)
        } catch (e: NoClassDefFoundError) {
            throw RuntimeException(
                "Failed to bootstrap Gradle. Check MANIFEST.MF 'Class-Path' of the entry-point " +
                        "'" + bootstrapName + "' and ensure there are no missing dependencies for the manifest classpath", e
            )
        }

        val oldClassLoader = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(runtimeClassLoader)

        try {
            val mainClass = runtimeClassLoader.loadClass(mainClassName)
            val entryPoint: Any = mainClass.getConstructor().newInstance()
            val mainMethod = mainClass.getMethod("run", Array<String>::class.java)
            mainMethod.invoke(entryPoint, args ?: emptyArray())
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader)

            ClassLoaderUtils.tryClose(runtimeClassLoader)
            ClassLoaderUtils.tryClose(antClassLoader)
        }
    }
}
