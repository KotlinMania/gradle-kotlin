/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import com.google.common.collect.ImmutableList
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.daemon.ProcessIsolatedCompilerWorkerExecutor
import org.gradle.api.internal.tasks.scala.ScalaCompilerFactory
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.scala.internal.ScalaCompileOptionsConfigurer
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.initialization.layout.ProjectCacheDir
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.scala.tasks.AbstractScalaCompile
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import java.io.File

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
@CacheableTask
abstract class ScalaCompile : AbstractScalaCompile() {
    /**
     * Returns the classpath to use to load the Scala compiler.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var scalaClasspath: FileCollection? = null

    /**
     * Returns the classpath to use to load the Zinc incremental compiler. This compiler in turn loads the Scala compiler.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var zincClasspath: FileCollection? = null
    /**
     * Returns the Scala compiler plugins to use.
     *
     * @since 6.4
     */
    /**
     * Sets the Scala compiler plugins to use.
     *
     * @param scalaCompilerPlugins Collection of Scala compiler plugins.
     * @since 6.4
     */
    @get:ToBeReplacedByLazyProperty
    @get:Classpath
    var scalaCompilerPlugins: FileCollection? = null
    private var compiler: Compiler<ScalaJavaJointCompileSpec?>? = null

    @Nested
    override fun getScalaCompileOptions(): ScalaCompileOptions? {
        return super.getScalaCompileOptions() as ScalaCompileOptions?
    }

    override fun createSpec(): ScalaJavaJointCompileSpec {
        ScalaCompileOptionsConfigurer.configure(getScalaCompileOptions(), getToolchain(), this.scalaClasspath!!.getFiles())
        val spec = super.createSpec()
        if (this.scalaCompilerPlugins != null) {
            spec.setScalaCompilerPlugins(ImmutableList.copyOf<File?>(this.scalaCompilerPlugins))
        }
        return spec
    }

    /**
     * For testing only.
     */
    fun setCompiler(compiler: Compiler<ScalaJavaJointCompileSpec?>?) {
        this.compiler = compiler
    }

    override fun getCompiler(spec: ScalaJavaJointCompileSpec?): Compiler<ScalaJavaJointCompileSpec?>? {
        assertScalaClasspathIsNonEmpty()
        if (compiler == null) {
            val workerDaemonFactory = getServices().get<WorkerDaemonFactory?>(WorkerDaemonFactory::class.java)
            val forkOptionsFactory = getServices().get<JavaForkOptionsFactory?>(JavaForkOptionsFactory::class.java)
            val classPathRegistry = getServices().get<ClassPathRegistry?>(ClassPathRegistry::class.java)
            val classLoaderRegistry = getServices().get<ClassLoaderRegistry?>(ClassLoaderRegistry::class.java)
            val actionExecutionSpecFactory = getServices().get<ActionExecutionSpecFactory?>(ActionExecutionSpecFactory::class.java)
            val projectCacheDir = getServices().get<ProjectCacheDir?>(ProjectCacheDir::class.java)
            val scalaCompilerFactory = ScalaCompilerFactory(
                getServices().get<WorkerDirectoryProvider?>(WorkerDirectoryProvider::class.java)!!.getWorkingDirectory(),
                ProcessIsolatedCompilerWorkerExecutor(workerDaemonFactory, actionExecutionSpecFactory, projectCacheDir!!), this.scalaClasspath,
                this.zincClasspath, forkOptionsFactory, classPathRegistry, classLoaderRegistry,
                getServices().get<ClasspathHasher?>(ClasspathHasher::class.java)
            )
            compiler = scalaCompilerFactory.newCompiler(spec)
        }
        return compiler
    }

    protected fun assertScalaClasspathIsNonEmpty() {
        if (this.scalaClasspath!!.isEmpty()) {
            throw InvalidUserDataException(
                ("'" + getName() + ".scalaClasspath' must not be empty. If a Scala compile dependency is provided, "
                        + "the 'scala-base' plugin will attempt to configure 'scalaClasspath' automatically. Alternatively, you may configure 'scalaClasspath' explicitly.")
            )
        }
    }
}
