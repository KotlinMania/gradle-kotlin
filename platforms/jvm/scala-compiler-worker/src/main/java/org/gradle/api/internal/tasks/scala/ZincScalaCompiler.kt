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

import com.google.common.collect.Iterables
import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.cache.internal.MapBackedCache
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.time.Time.startTimer
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.GFileUtils
import sbt.internal.inc.Analysis
import sbt.internal.inc.IncrementalCompilerImpl
import sbt.internal.inc.Locate
import sbt.internal.inc.LoggedReporter
import sbt.internal.inc.PlainVirtualFileConverter
import sbt.internal.inc.ScalaInstance
import scala.Function1
import scala.Option
import xsbti.CompileFailed
import xsbti.Position
import xsbti.T2
import xsbti.VirtualFile
import xsbti.compile.AnalysisContents
import xsbti.compile.AnalysisStore
import xsbti.compile.ClassFileManagerType
import xsbti.compile.ClasspathOptionsUtil
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompileOptions
import xsbti.compile.CompileProgress
import xsbti.compile.CompilerCache
import xsbti.compile.DefinesClass
import xsbti.compile.IncOptions
import xsbti.compile.MiniSetup
import xsbti.compile.PerClasspathEntryLookup
import xsbti.compile.PreviousResult
import xsbti.compile.ScalaCompiler
import xsbti.compile.TransactionalManagerType
import java.io.File
import java.nio.file.Path
import java.util.LinkedList
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Supplier
import javax.inject.Inject

class ZincScalaCompiler @Inject constructor(private val scalaInstance: ScalaInstance?, private val scalaCompiler: ScalaCompiler?, private val analysisStoreProvider: AnalysisStoreProvider) :
    Compiler<ScalaJavaJointCompileSpec?> {
    private val definesClassCache = MapBackedCache<VirtualFile?, DefinesClass?>(ConcurrentHashMap<VirtualFile?, DefinesClass?>())

    override fun execute(spec: ScalaJavaJointCompileSpec): WorkResult {
        LOGGER!!.info("Compiling with Zinc Scala compiler.")

        val timer = startTimer()

        val incremental = IncrementalCompilerImpl()

        val compilers = incremental.compilers(scalaInstance, ClasspathOptionsUtil.boot(), Option.apply<Path?>(Jvm.current().getJavaHome().toPath()), scalaCompiler)

        val scalacOptions = ZincScalaCompilerArgumentsGenerator().generate(spec)
        val javacOptions = JavaCompilerArgumentsBuilder(spec).includeClasspath(false).noEmptySourcePath().build()

        val classpath: MutableList<VirtualFile?> = LinkedList<VirtualFile?>()
        for (classpathEntry in spec.compileClasspath!!) {
            classpath.add(CONVERTER.toVirtualFile(classpathEntry!!.toPath()))
        }
        val sourceFiles: MutableList<VirtualFile?> = LinkedList<VirtualFile?>()
        for (f in spec.getSourceFiles()!!) {
            sourceFiles.add(CONVERTER.toVirtualFile(f!!.toPath()))
        }
        val compileOptions = CompileOptions.create()
            .withSources(Iterables.toArray<VirtualFile?>(sourceFiles, VirtualFile::class.java))
            .withClasspath(Iterables.toArray<VirtualFile?>(classpath, VirtualFile::class.java))
            .withScalacOptions(scalacOptions.toTypedArray<String?>())
            .withClassesDirectory(spec.getDestinationDir()!!.toPath())
            .withJavacOptions(javacOptions.toTypedArray<String?>())

        val analysisFile = spec.getAnalysisFile()
        val analysisStore: Optional<AnalysisStore?>?
        val classFileManagerType: Optional<ClassFileManagerType?>?
        if (spec.getScalaCompileOptions().isForce()) {
            analysisStore = Optional.empty<AnalysisStore?>()
            classFileManagerType = IncOptions.defaultClassFileManagerType()
        } else {
            analysisStore = Optional.of<AnalysisStore?>(analysisStoreProvider.get(analysisFile))
            classFileManagerType = Optional.of<ClassFileManagerType?>(TransactionalManagerType.of(spec.getClassfileBackupDir(), SbtLoggerAdapter()))
        }

        val previousResult = analysisStore.flatMap<PreviousResult>(Function { store: AnalysisStore? ->
            store!!.get()
                .map<PreviousResult?>(Function { a: AnalysisContents? -> PreviousResult.of(Optional.of<CompileAnalysis?>(a!!.getAnalysis()), Optional.of<MiniSetup?>(a.getMiniSetup())) })
        })
            .orElse(PreviousResult.of(Optional.empty<CompileAnalysis?>(), Optional.empty<MiniSetup?>()))

        val incOptions = IncOptions.of()
            .withRecompileOnMacroDef(Optional.of<Boolean?>(false))
            .withClassfileManagerType(classFileManagerType)
            .withTransitiveStep(5)

        val setup = incremental.setup(
            ZincScalaCompiler.EntryLookup(spec),
            false,
            analysisFile.toPath(),
            CompilerCache.fresh(),
            incOptions,  // MappedPosition is used to make sure toString returns proper error messages
            LoggedReporter(100, SbtLoggerAdapter(), Function1 { delegate: Position? -> MappedPosition(delegate) }),
            Option.empty<CompileProgress?>(),
            Option.empty<AnalysisStore?>(),
            extra
        )

        val inputs = incremental.inputs(compileOptions, compilers, setup, previousResult)
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(inputs.toString())
        }
        if (spec.getScalaCompileOptions().isForce()) {
            // TODO This should use Deleter
            GFileUtils.deleteDirectory(spec.getDestinationDir())
            GFileUtils.deleteQuietly(spec.getAnalysisFile())
        }
        LOGGER.info("Prepared Zinc Scala inputs: {}", timer.elapsed)

        try {
            val compile = incremental.compile(inputs, SbtLoggerAdapter())
            if (analysisStore.isPresent()) {
                val contentNext = AnalysisContents.create(compile.analysis(), compile.setup())
                analysisStore.get().set(contentNext)
            }
        } catch (e: CompileFailed) {
            throw CompilationFailedException(e)
        }
        LOGGER.info("Completed Scala compilation: {}", timer.elapsed)
        return WorkResults.didWork(true)
    }

    private inner class EntryLookup(spec: ScalaJavaJointCompileSpec) : PerClasspathEntryLookup {
        private val analysisMap: MutableMap<VirtualFile?, File?>

        init {
            this.analysisMap = HashMap<VirtualFile?, File?>()
            analysisMap.put(CONVERTER.toVirtualFile(spec.getDestinationDir()!!.toPath()), spec.getAnalysisFile())
            for (e in spec.getAnalysisMap().entries) {
                analysisMap.put(CONVERTER.toVirtualFile(e.key.toPath()), e.value)
            }
        }

        override fun analysis(classpathEntry: VirtualFile?): Optional<CompileAnalysis?> {
            return Optional.ofNullable<File?>(analysisMap.get(classpathEntry))
                .flatMap<CompileAnalysis?>(Function { f: File? -> analysisStoreProvider.get(f).get().map<CompileAnalysis?>(Function { obj: AnalysisContents? -> obj!!.getAnalysis() }) })
        }

        override fun definesClass(classpathEntry: VirtualFile): DefinesClass {
            if (classpathEntry.name() == "rt.jar") {
                return DefinesClass { className: String? -> false }
            }
            return analysis(classpathEntry)
                .map<Analysis?>(Function { a: CompileAnalysis? -> if (a is Analysis) a else null })
                .map<DefinesClass>(Function { analysis: Analysis? -> AnalysisBakedDefineClass(analysis!!) })
                .orElseGet(Supplier { definesClassCache.get(classpathEntry, Function { entry0: VirtualFile? -> Locate.definesClass(entry0) }) })
        }
    }

    private class AnalysisBakedDefineClass(private val analysis: Analysis) : DefinesClass {
        override fun apply(className: String?): Boolean {
            return analysis.relations().productClassName().reverse(className).nonEmpty()
        }
    }

    companion object {
        private val LOGGER = getLogger(ZincScalaCompiler::class.java)

        private val CONVERTER: PlainVirtualFileConverter = PlainVirtualFileConverter.converter()

        private val extra: Array<T2<String?, String?>?>
            get() = arrayOfNulls<T2<*, *>>(0)
    }
}
