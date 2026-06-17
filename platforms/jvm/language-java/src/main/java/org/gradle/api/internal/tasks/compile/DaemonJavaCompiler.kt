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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler
import org.gradle.api.internal.tasks.compile.daemon.CompilerParameters
import org.gradle.api.internal.tasks.compile.daemon.CompilerWorkerExecutor
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.workers.internal.DaemonForkOptions
import org.gradle.workers.internal.DaemonForkOptionsBuilder
import org.gradle.workers.internal.FlatClassLoaderStructure
import org.gradle.workers.internal.KeepAliveMode
import java.io.File

class DaemonJavaCompiler(
    private val daemonWorkingDir: File?,
    private val javaCompilerFactory: JavaHomeBasedJavaCompilerFactory?,
    compilerWorkerExecutor: CompilerWorkerExecutor?,
    private val forkOptionsFactory: JavaForkOptionsFactory,
    private val classPathRegistry: ClassPathRegistry
) : AbstractDaemonCompiler<JavaCompileSpec?>(compilerWorkerExecutor) {
    override fun getCompilerParameters(spec: JavaCompileSpec?): CompilerParameters {
        return JavaCompilerParameters(JdkJavaCompiler::class.java.getName(), arrayOf<Any?>(javaCompilerFactory), spec)
    }

    override fun getAdditionalCompilerServices(): MutableSet<Class<*>?> {
        return mutableSetOf<Class<*>?>()
    }

    override fun toDaemonForkOptions(spec: JavaCompileSpec): DaemonForkOptions? {
        require(spec is ForkingJavaCompileSpec) { String.format("Expected a %s, but got %s", ForkingJavaCompileSpec::class.java.getSimpleName(), spec.javaClass.getSimpleName()) }
        val forkingSpec = spec as ForkingJavaCompileSpec

        val jvm = Jvm.forHome((spec as ForkingJavaCompileSpec).javaHome)

        val forkOptions: MinimalJavaCompilerDaemonForkOptions = spec.compileOptions!!.forkOptions!!
        val javaForkOptions = BaseForkOptionsConverter(forkOptionsFactory).transform(forkOptions)
        javaForkOptions!!.setWorkingDir(daemonWorkingDir)
        javaForkOptions.setExecutable(jvm.getJavaExecutable())

        var compilerClasspath = classPathRegistry.getClassPath("JAVA-COMPILER")

        val javaLanguageVersion = JavaLanguageVersion.of(forkingSpec.javaLanguageVersion)
        if (javaLanguageVersion.canCompileOrRun(9)) {
            // In JDK 9 and above the compiler internal classes are bundled with the rest of the JDK, but we need to export it to gain access.
            javaForkOptions.jvmArgs(
                "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
            )
        } else {
            // In JDK 8 and below, the compiler internal classes are in tools.jar.
            val toolsJar = jvm.getToolsJar()
            checkNotNull(toolsJar) { "Could not find tools.jar in " + jvm.getJavaHome() }

            compilerClasspath = compilerClasspath.plus(
                mutableListOf<File?>(toolsJar)
            )
        }

        val classLoaderStructure = FlatClassLoaderStructure(VisitableURLClassLoader.Spec("compiler", compilerClasspath.getAsURLs()))
        return DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .withClassLoaderStructure(classLoaderStructure)
            .keepAliveMode(KeepAliveMode.DAEMON)
            .build()
    }
}
