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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter
import org.gradle.api.internal.tasks.compile.MinimalJavaCompilerDaemonForkOptions
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler
import org.gradle.api.internal.tasks.compile.daemon.CompilerParameters
import org.gradle.api.internal.tasks.compile.daemon.CompilerWorkerExecutor
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.workers.internal.DaemonForkOptions
import org.gradle.workers.internal.DaemonForkOptionsBuilder
import org.gradle.workers.internal.HierarchicalClassLoaderStructure
import org.gradle.workers.internal.KeepAliveMode
import java.io.File

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

class DaemonScalaCompiler<T : ScalaJavaJointCompileSpec?>(
    private val daemonWorkingDir: File?,
    private val hashedScalaClasspath: HashedClasspath?,
    compilerWorkerExecutor: CompilerWorkerExecutor,
    private val zincClasspath: Iterable<File?>?,
    private val forkOptionsFactory: JavaForkOptionsFactory,
    private val classPathRegistry: ClassPathRegistry,
    private val classLoaderRegistry: ClassLoaderRegistry
) : AbstractDaemonCompiler<T?>(compilerWorkerExecutor) {
    override fun getCompilerParameters(spec: T?): CompilerParameters? {
        return ScalaCompilerParameters<T?>(ZincScalaCompilerFacade::class.java.getName(), arrayOf<Any?>(hashedScalaClasspath), spec)
    }

    val additionalCompilerServices: MutableSet<Class<*>?>?
        get() = mutableSetOf<Class<*>?>(GlobalScopedCacheBuilderFactory::class.java)

    override fun toDaemonForkOptions(spec: T?): DaemonForkOptions? {
        val javaOptions: MinimalJavaCompilerDaemonForkOptions = spec!!.compileOptions!!.forkOptions!!
        val compileOptions = spec.scalaCompileOptions
        val forkOptions = compileOptions.forkOptions
        val javaForkOptions = BaseForkOptionsConverter(forkOptionsFactory).transform(mergeForkOptions(javaOptions, forkOptions))
        javaForkOptions!!.systemProperty("xsbt.skip.cp.lookup", true)
        javaForkOptions.setWorkingDir(daemonWorkingDir)
        javaForkOptions.setExecutable(spec.javaExecutable.getAbsolutePath())

        val compilerClasspath = classPathRegistry.getClassPath("SCALA-COMPILER").plus(DefaultClassPath.of(zincClasspath))

        val classLoaderStructure = HierarchicalClassLoaderStructure(classLoaderRegistry.getGradleWorkerExtensionSpec())
            .withChild(this.scalaFilterSpec)
            .withChild(VisitableURLClassLoader.Spec("compiler", compilerClasspath.getAsURLs()))

        return DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .withClassLoaderStructure(classLoaderStructure)
            .keepAliveMode(KeepAliveMode.valueOf(compileOptions.keepAliveMode.name))
            .build()
    }

    private val scalaFilterSpec: FilteringClassLoader.Spec
        get() {
            val gradleApiAndScalaSpec = classLoaderRegistry.getGradleApiFilterSpec()

            // These should come from the compiler classloader
            gradleApiAndScalaSpec.disallowPackage("org.gradle.api.internal.tasks.scala")

            // Guava
            gradleApiAndScalaSpec.allowPackage("com.google")

            return gradleApiAndScalaSpec
        }
}

