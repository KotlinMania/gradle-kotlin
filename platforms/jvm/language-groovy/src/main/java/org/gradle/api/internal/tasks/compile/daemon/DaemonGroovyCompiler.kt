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
package org.gradle.api.internal.tasks.compile.daemon

import com.google.common.collect.Iterables
import org.gradle.api.Action
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.ApiCompilerResult
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter
import org.gradle.api.internal.tasks.compile.DaemonSideCompiler
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompilerDaemonForkOptions
import org.gradle.api.internal.tasks.compile.MinimalJavaCompilerDaemonForkOptions
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup.compilation
import org.gradle.api.problems.internal.ProblemReporterInternal
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.jvm.JpmsConfiguration
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.workers.internal.DaemonForkOptions
import org.gradle.workers.internal.DaemonForkOptionsBuilder
import org.gradle.workers.internal.HierarchicalClassLoaderStructure
import org.gradle.workers.internal.KeepAliveMode
import java.io.File

class DaemonGroovyCompiler(
    private val daemonWorkingDir: File?,
    private val classPathRegistry: ClassPathRegistry,
    compilerWorkerExecutor: CompilerWorkerExecutor?,
    private val classLoaderRegistry: ClassLoaderRegistry,
    private val forkOptionsFactory: JavaForkOptionsFactory,
    private val jvmVersionDetector: JvmVersionDetector,
    private val problemReporter: ProblemReporterInternal
) : AbstractDaemonCompiler<GroovyJavaJointCompileSpec?>(compilerWorkerExecutor) {
    override fun getCompilerParameters(spec: GroovyJavaJointCompileSpec?): CompilerParameters {
        return GroovyCompilerParameters(DaemonSideCompiler::class.java.getName(), arrayOf<Any?>(classPathRegistry.getClassPath("JAVA-COMPILER-PLUGIN").getAsFiles()), spec)
    }

    override fun getAdditionalCompilerServices(): MutableSet<Class<*>?> {
        return mutableSetOf<Class<*>?>()
    }

    override fun toDaemonForkOptions(spec: GroovyJavaJointCompileSpec): DaemonForkOptions? {
        val javaOptions: MinimalJavaCompilerDaemonForkOptions = spec.compileOptions!!.forkOptions!!
        val groovyOptions: MinimalGroovyCompilerDaemonForkOptions = spec.groovyCompileOptions!!.forkOptions!!
        // Ant is optional dependency of groovy(-all) module but mandatory dependency of Groovy compiler;
        // that's why we add it here. The following assumes that any Groovy compiler version supported by Gradle
        // is compatible with Gradle's current Ant version.
        val antFiles: MutableCollection<File?>? = classPathRegistry.getClassPath("ANT").getAsFiles()
        val classpath: Iterable<File?> = Iterables.< File > concat < java . io . File ? > (spec.groovyClasspath, antFiles)
        val targetGroovyClasspath = VisitableURLClassLoader.Spec("worker-loader", DefaultClassPath.of(classpath).getAsURLs())

        var languageGroovyClasspath = classPathRegistry.getClassPath("GROOVY-COMPILER")

        val gradleAndUserFilter: FilteringClassLoader.Spec = minimalGradleFilter

        for (sharedPackage in SHARED_PACKAGES) {
            gradleAndUserFilter.allowPackage(sharedPackage)
        }

        val javaForkOptions = BaseForkOptionsConverter(forkOptionsFactory).transform(mergeForkOptions(javaOptions, groovyOptions))
        javaForkOptions!!.setWorkingDir(daemonWorkingDir)
        javaForkOptions.setExecutable(javaOptions.executable)
        val javaVersionMajor = jvmVersionDetector.getJavaVersionMajor(javaOptions.executable)
        javaForkOptions.jvmArgs(JpmsConfiguration.forGroovyCompilerWorker(javaVersionMajor))
        if (javaVersionMajor <= 8) {
            // In JDK 8 and below, we need to attach the 'tools.jar' to the classpath.
            val javaExecutable = File(javaForkOptions.getExecutable())
            val jvm = Jvm.forHome(javaExecutable.getParentFile().getParentFile())
            val toolsJar = jvm.getToolsJar()
            if (toolsJar == null) {
                val contextualMessage = String.format("The 'tools.jar' cannot be found in the JDK '%s'.", jvm.getJavaHome())
                val problemId = ProblemId.create("missing-tools-jar", "Missing tools.jar", compilation().groovy()!!)
                throw problemReporter.throwing(IllegalStateException(contextualMessage), problemId, Action { problemSpec: ProblemSpec? ->
                    problemSpec!!
                        .contextualLabel(contextualMessage)!!
                        .solution("Check if the installation is not a JRE but a JDK.")
                }
                )
            } else {
                languageGroovyClasspath = languageGroovyClasspath.plus(mutableListOf<File?>(toolsJar))
            }
        }

        val compilerClasspath = VisitableURLClassLoader.Spec("compiler-loader", languageGroovyClasspath.getAsURLs())
        val classLoaderStructure =
            HierarchicalClassLoaderStructure(classLoaderRegistry.getGradleWorkerExtensionSpec())
                .withChild(minimalGradleFilter)
                .withChild(targetGroovyClasspath)
                .withChild(gradleAndUserFilter)
                .withChild(compilerClasspath)

        return DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .keepAliveMode(KeepAliveMode.SESSION)
            .withClassLoaderStructure(classLoaderStructure)
            .build()
    }

    companion object {
        private val SHARED_PACKAGES: Iterable<String> =
            mutableListOf<String?>("groovy", "org.codehaus.groovy", "groovyjarjarantlr", "groovyjarjarasm", "groovyjarjarcommonscli", "org.apache.tools.ant", "com.sun.tools.javac")
        private val minimalGradleFilter: FilteringClassLoader.Spec
            get() {
                // Allow only certain things from the underlying classloader
                val gradleFilterSpec = FilteringClassLoader.Spec()

                // Logging
                gradleFilterSpec.allowPackage("org.slf4j")

                // Native Services
                gradleFilterSpec.allowPackage("net.rubygrapefruit.platform")

                // Inject
                gradleFilterSpec.allowPackage("javax.inject")

                // Gradle stuff
                gradleFilterSpec.allowPackage("org.gradle")

                // Guava
                gradleFilterSpec.allowPackage("com.google")

                // This should come from the compiler classpath only
                gradleFilterSpec.disallowPackage("org.gradle.api.internal.tasks.compile")

                /*
     * This shouldn't be necessary, but currently is because the worker API handles return types differently
     * depending on whether you use process isolation or classpath isolation. In the former case, the return
     * value is serialized and deserialized, so the correct class is returned. In the latter case, the result
     * is returned directly, which means it is not an instance of the expected class unless we allow that class
     * to leak through here. Should be fixed in the worker API, so that it always serializes/deserializes results.
     */
                gradleFilterSpec.allowClass(ApiCompilerResult::class.java)
                gradleFilterSpec.allowClass(AnnotationProcessingResult::class.java)
                gradleFilterSpec.allowClass(ConstantsAnalysisResult::class.java)

                return gradleFilterSpec
            }
    }
}
