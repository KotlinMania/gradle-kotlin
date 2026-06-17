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
package org.gradle.api.internal.tasks.compile

import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyShell
import groovy.lang.GroovySystem
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.messages.ExceptionMessage
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SimpleMessage
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit
import org.codehaus.groovy.tools.javac.JavaCompiler
import org.codehaus.groovy.tools.javac.JavaCompilerFactory
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.FileUtils
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.groovyloader.GroovySystemLoaderFactory
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.io.Serializable
import java.lang.Boolean
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.Any
import kotlin.Array
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.String
import kotlin.Throws
import kotlin.UnsupportedOperationException
import kotlin.arrayOf

class ApiGroovyCompiler(private val javaCompiler: Compiler<JavaCompileSpec?>, private val objectFactory: ObjectFactory) : Compiler<GroovyJavaJointCompileSpec?>, Serializable {
    private abstract class IncrementalCompilationCustomizer : CompilationCustomizer(CompilePhase.CLASS_GENERATION) {
        abstract fun addToConfiguration(configuration: CompilerConfiguration?)

        companion object {
            fun fromSpec(spec: GroovyJavaJointCompileSpec, result: ApiCompilerResult): IncrementalCompilationCustomizer {
                if (spec.incrementalCompilationEnabled()) {
                    return TrackingClassGenerationCompilationCustomizer(CompilationSourceDirs(spec), result, CompilationClassBackupService(spec, result))
                } else {
                    return NoOpCompilationCustomizer()
                }
            }
        }
    }

    private class NoOpCompilationCustomizer : IncrementalCompilationCustomizer() {
        public override fun addToConfiguration(configuration: CompilerConfiguration?) {
        }

        @Throws(CompilationFailedException::class)
        override fun call(source: SourceUnit?, context: GeneratorContext?, classNode: ClassNode?) {
            throw UnsupportedOperationException()
        }
    }

    private class TrackingClassGenerationCompilationCustomizer(
        private val compilationSourceDirs: CompilationSourceDirs,
        private val result: ApiCompilerResult,
        private val compilationClassBackupService: CompilationClassBackupService
    ) : IncrementalCompilationCustomizer() {
        override fun call(source: SourceUnit, context: GeneratorContext?, classNode: ClassNode) {
            inspectClassNode(source, classNode)
        }

        fun inspectClassNode(sourceUnit: SourceUnit, classNode: ClassNode) {
            val classFqName = classNode.getName()
            val relativePath = compilationSourceDirs.relativize(File(sourceUnit.getSource().getURI().getPath())).orElseThrow<java.lang.IllegalStateException?>(Supplier { IllegalStateException() })
            result.getSourceClassesMapping().computeIfAbsent(relativePath) { key: String? -> HashSet<String?>() }.add(classFqName)
            compilationClassBackupService.maybeBackupClassFile(classFqName)
            val iterator = classNode.getInnerClasses()
            while (iterator.hasNext()) {
                inspectClassNode(sourceUnit, iterator.next()!!)
            }
        }

        public override fun addToConfiguration(configuration: CompilerConfiguration) {
            configuration.addCompilationCustomizers(this)
        }
    }

    private fun getSortedSourceFiles(spec: GroovyJavaJointCompileSpec): Array<File?> {
        // Sort source files to work around https://issues.apache.org/jira/browse/GROOVY-7966
        val sortedSourceFiles = Iterables.toArray<File?>(spec.getSourceFiles(), File::class.java)
        Arrays.sort(sortedSourceFiles)
        return sortedSourceFiles
    }

    override fun execute(spec: GroovyJavaJointCompileSpec): WorkResult {
        val result = ApiCompilerResult()
        result.annotationProcessingResult.fullRebuildCause = "Incremental annotation processing is not supported by Groovy."
        val groovySystemLoaderFactory = GroovySystemLoaderFactory()
        val compilerClassLoader = this.javaClass.getClassLoader()
        val compilerGroovyLoader = groovySystemLoaderFactory.forClassLoader(compilerClassLoader)

        val configuration = CompilerConfiguration()
        configuration.setVerbose(spec.getGroovyCompileOptions().isVerbose())
        configuration.setSourceEncoding(spec.getGroovyCompileOptions().getEncoding())
        configuration.setTargetBytecode(spec.targetCompatibility)
        configuration.setTargetDirectory(spec.getDestinationDir())
        Companion.canonicalizeValues(spec.getGroovyCompileOptions().getOptimizationOptions()!!)

        val version = parseGroovyVersion()
        if (version.compareTo(VersionNumber.parse("2.5")) >= 0) {
            configuration.setParameters(spec.getGroovyCompileOptions().isParameters())
        } else if (spec.getGroovyCompileOptions().isParameters()) {
            throw GradleException("Using Groovy compiler flag '--parameters' requires Groovy 2.5+ but found Groovy " + version)
        }

        val customizer: IncrementalCompilationCustomizer = IncrementalCompilationCustomizer.Companion.fromSpec(spec, result)
        customizer.addToConfiguration(configuration)

        if (spec.getGroovyCompileOptions().getConfigurationScript() != null) {
            applyConfigurationScript(spec.getGroovyCompileOptions().getConfigurationScript()!!, configuration)
        }
        try {
            configuration.setOptimizationOptions(spec.getGroovyCompileOptions().getOptimizationOptions())
        } catch (ignored: NoSuchMethodError) { /* method was only introduced in Groovy 1.8 */
        }

        try {
            configuration.setDisabledGlobalASTTransformations(spec.getGroovyCompileOptions().getDisabledGlobalASTTransformations())
        } catch (ignored: NoSuchMethodError) { /* method was only introduced in Groovy 2.0.0 */
        }

        val jointCompilationOptions: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        val stubDir = spec.getGroovyCompileOptions().getStubDir()
        stubDir.mkdirs()
        jointCompilationOptions.put("stubDir", stubDir)
        jointCompilationOptions.put("keepStubs", spec.getGroovyCompileOptions().isKeepStubs())
        configuration.setJointCompilationOptions(jointCompilationOptions)

        val classPathLoader: ClassLoader?
        if (version.compareTo(VersionNumber.parse("2.0")) < 0) {
            // using a transforming classloader is only required for older buggy Groovy versions
            classPathLoader = GroovyCompileTransformingClassLoader(this.extClassLoader, DefaultClassPath.of(spec.compileClasspath))
        } else {
            classPathLoader = DefaultClassLoaderFactory().createIsolatedClassLoader("api-groovy-compile-loader", DefaultClassPath.of(spec.compileClasspath))
        }
        val compileClasspathClassLoader = GroovyClassLoader(classPathLoader, null)
        val compileClasspathLoader = groovySystemLoaderFactory.forClassLoader(classPathLoader)

        val groovyCompilerClassLoaderSpec = FilteringClassLoader.Spec()
        groovyCompilerClassLoaderSpec.allowPackage("org.codehaus.groovy")
        groovyCompilerClassLoaderSpec.allowPackage("groovy")
        groovyCompilerClassLoaderSpec.allowPackage("groovyjarjarasm")
        // Disallow classes from Groovy Jar that reference external classes. Such classes must be loaded from astTransformClassLoader,
        // or a NoClassDefFoundError will occur. Essentially this is drawing a line between the Groovy compiler and the Groovy
        // library, albeit only for selected classes that run a high risk of being statically referenced from a transform.
        groovyCompilerClassLoaderSpec.disallowClass("groovy.util.GroovyTestCase")
        groovyCompilerClassLoaderSpec.disallowClass("groovy.test.GroovyTestCase")
        groovyCompilerClassLoaderSpec.disallowClass("org.codehaus.groovy.transform.NotYetImplementedASTTransformation")
        groovyCompilerClassLoaderSpec.disallowPackage("groovy.servlet")
        val groovyCompilerClassLoader = FilteringClassLoader(GroovyClassLoader::class.java.getClassLoader(), groovyCompilerClassLoaderSpec)

        // AST transforms need their own class loader that shares compiler classes with the compiler itself
        val astTransformClassLoader = GroovyClassLoader(groovyCompilerClassLoader, null)
        // can't delegate to compileClasspathLoader because this would result in ASTTransformation interface
        // (which is implemented by the transform class) being loaded by compileClasspathClassLoader (which is
        // where the transform class is loaded from)
        for (file in spec.compileClasspath) {
            astTransformClassLoader.addClasspath(file.getPath())
        }
        val unit: JavaAwareCompilationUnit = object : JavaAwareCompilationUnit(configuration, compileClasspathClassLoader) {
            override fun getTransformLoader(): GroovyClassLoader {
                return astTransformClassLoader
            }
        }

        val shouldProcessAnnotations: Boolean = shouldProcessAnnotations(spec)
        if (shouldProcessAnnotations) {
            // If an annotation processor is detected, we need to force Java stub generation, so the we can process annotations on Groovy classes
            // We are forcing stub generation by tricking the groovy compiler into thinking there are java files to compile.
            // All java files are just passed to the compile method of the JavaCompiler and aren't processed internally by the Groovy Compiler.
            // Since we're maintaining our own list of Java files independent of what's passed by the Groovy compiler, adding a non-existent java file
            // to the sources won't cause any issues.
            unit.addSources(arrayOf<File>(File("ForceStubGeneration.java")))
        }
        unit.addSources(getSortedSourceFiles(spec))

        unit.setCompilerFactory(object : JavaCompilerFactory {
            override fun createCompiler(config: CompilerConfiguration?): JavaCompiler {
                return object : JavaCompiler {
                    override fun compile(files: MutableList<String?>?, cu: CompilationUnit) {
                        if (shouldProcessAnnotations) {
                            // In order for the Groovy stubs to have annotation processors invoked against them, they must be compiled as source.
                            // Classes compiled as a result of being on the -sourcepath do not have the annotation processor run against them
                            spec.setSourceFiles(Iterables.concat<File?>(spec.getSourceFiles(), objectFactory.fileTree().from(stubDir)))
                        } else {
                            // When annotation processing isn't required, it's better to add the Groovy stubs as part of the source path.
                            // This allows compilations to complete faster, because only the Groovy stubs that are needed by the java source are compiled.
                            val sourcepathBuilder = ImmutableList.builder<File?>()
                            sourcepathBuilder.add(stubDir)
                            if (spec.compileOptions.sourcepath != null) {
                                sourcepathBuilder.addAll(spec.compileOptions.sourcepath)
                            }
                            spec.compileOptions.setSourcepath(sourcepathBuilder.build())
                        }

                        spec.setSourceFiles(Iterables.filter<File?>(spec.getSourceFiles(), object : Predicate<File?> {
                            override fun apply(file: File): Boolean {
                                return FileUtils.hasExtension(file, ".java")
                            }
                        }))

                        try {
                            val javaCompilerResult = javaCompiler.execute(spec)
                            if (javaCompilerResult is ApiCompilerResult) {
                                copyJavaCompilerResult(javaCompilerResult)
                            }
                        } catch (e: org.gradle.api.internal.tasks.compile.CompilationFailedException) {
                            val partialResult = e.getCompilerPartialResult()
                            partialResult.ifPresent(Consumer { result: ApiCompilerResult? -> copyJavaCompilerResult(result!!) })
                            cu.getErrorCollector().addFatalError(SimpleMessage(e.message, cu))
                        }
                    }
                }
            }

            fun copyJavaCompilerResult(javaCompilerResult: ApiCompilerResult) {
                result.getSourceClassesMapping().putAll(javaCompilerResult.getSourceClassesMapping())
                result.backupClassFiles.putAll(javaCompilerResult.backupClassFiles)
            }
        })

        try {
            unit.compile()
            return result
        } catch (e: CompilationFailedException) {
            if (isFatalException(e)) {
                // This indicates a compiler bug and not a user error,
                // so we cannot recover from such error: we need to force full recompilation.
                throw CompilationFatalException(e)
            }

            System.err.println(e.message)
            // Explicit flush, System.err is an auto-flushing PrintWriter unless it is replaced.
            System.err.flush()
            throw CompilationFailedException(result)
        } finally {
            // Remove compile and AST types from the Groovy loader
            compilerGroovyLoader.discardTypesFrom(classPathLoader)
            compilerGroovyLoader.discardTypesFrom(astTransformClassLoader)
            //Discard the compile loader
            compileClasspathLoader.shutdown()
            CompositeStoppable.stoppable(classPathLoader, astTransformClassLoader).stop()
        }
    }

    private fun applyConfigurationScript(configScript: File, configuration: CompilerConfiguration?) {
        val version = parseGroovyVersion()
        if (version.compareTo(VersionNumber.parse("2.1")) < 0) {
            throw GradleException("Using a Groovy compiler configuration script requires Groovy 2.1+ but found Groovy " + version + "")
        }
        val binding = Binding()
        binding.setVariable("configuration", configuration)

        val configuratorConfig = CompilerConfiguration()
        val customizer = ImportCustomizer()
        customizer.addStaticStars("org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder")
        configuratorConfig.addCompilationCustomizers(customizer)

        val shell = GroovyShell(binding, configuratorConfig)
        try {
            shell.evaluate(configScript)
        } catch (e: Exception) {
            throw GradleException("Could not execute Groovy compiler configuration script: " + configScript.getAbsolutePath(), e)
        }
    }

    private fun parseGroovyVersion(): VersionNumber {
        var version: String?
        try {
            version = GroovySystem.getVersion()
        } catch (e: NoSuchMethodError) {
            // for Groovy <1.6, we need to call org.codehaus.groovy.runtime.InvokerHelper#getVersion
            try {
                val ih = Class.forName("org.codehaus.groovy.runtime.InvokerHelper")
                val getVersion = ih.getDeclaredMethod("getVersion")
                version = getVersion.invoke(ih) as String?
            } catch (e1: Exception) {
                throw GradleException("Unable to determine Groovy version.", e1)
            }
        }
        return VersionNumber.parse(version)
    }

    private val extClassLoader: ClassLoader
        get() = ClassLoaderUtils.getPlatformClassLoader()

    companion object {
        /**
         * Returns true if the exception is fatal, unrecoverable for the incremental compilation. Example of such error:
         * <pre>
         * error: startup failed:
         * General error during instruction selection: java.lang.NoClassDefFoundError: Unable to load class ClassName due to missing dependency DependencyName
         * java.lang.RuntimeException: java.lang.NoClassDefFoundError: Unable to load class ClassName due to missing dependency DependencyName
         * at org.codehaus.groovy.control.CompilationUnit$IPrimaryClassNodeOperation.doPhaseOperation(CompilationUnit.java:977)
         * at org.codehaus.groovy.control.CompilationUnit.processPhaseOperations(CompilationUnit.java:672)
         * at org.codehaus.groovy.control.CompilationUnit.compile(CompilationUnit.java:636)
         * at org.codehaus.groovy.control.CompilationUnit.compile(CompilationUnit.java:611)
        </pre> *
         */
        private fun isFatalException(e: CompilationFailedException?): Boolean {
            if (e is MultipleCompilationErrorsException) {
                // Groovy compiler wraps any uncontrolled exception (e.g. IOException, NoClassDefFoundError and similar) in a `ExceptionMessage`
                return e.getErrorCollector().getErrors().stream()
                    .anyMatch { message: Message? -> message is ExceptionMessage }
            }
            return false
        }

        private fun shouldProcessAnnotations(spec: GroovyJavaJointCompileSpec): Boolean {
            return spec.getGroovyCompileOptions().isJavaAnnotationProcessing() && spec.annotationProcessingConfigured()
        }

        // Make sure that map only contains Boolean.TRUE and Boolean.FALSE values and no other Boolean instances.
        // This is necessary because:
        // 1. serialization/deserialization of the compile spec doesn't preserve Boolean.TRUE/Boolean.FALSE but creates new instances
        // 1. org.codehaus.groovy.classgen.asm.WriterController makes identity comparisons
        private fun canonicalizeValues(options: MutableMap<String?, Boolean?>) {
            // unboxing and boxing does the trick
            options.replaceAll { k: String?, v: Boolean? -> if (v) Boolean.TRUE else Boolean.FALSE }
        }
    }
}
