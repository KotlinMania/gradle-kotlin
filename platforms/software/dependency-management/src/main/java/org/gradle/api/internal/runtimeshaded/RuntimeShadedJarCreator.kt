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

import com.google.common.base.Joiner
import com.google.common.collect.Iterables
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.internal.classpath.ClasspathBuilder
import org.gradle.internal.classpath.ClasspathEntryVisitor
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.concurrent.MultiProducerSingleConsumerProcessor
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.progress.PercentageProgressFormatter
import org.gradle.model.internal.asm.AsmConstants
import org.jspecify.annotations.NullMarked
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Arrays
import java.util.Objects
import java.util.PriorityQueue
import java.util.function.Consumer

@NullMarked
internal class RuntimeShadedJarCreator(
    private val progressLoggerFactory: ProgressLoggerFactory,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val remapper: ImplementationDependencyRelocator,
    private val classpathWalker: ClasspathWalker,
    private val classpathBuilder: ClasspathBuilder
) {
    fun create(type: RuntimeShadedJarType, outputJar: File, files: MutableCollection<out File>) {
        LOGGER.info("Generating " + type.getDisplayName() + ": " + outputJar.getAbsolutePath())
        val progressLogger = progressLoggerFactory.newOperation(RuntimeShadedJarCreator::class.java)
        progressLogger!!.setDescription("Generating " + type.getDisplayName())
        progressLogger.started()
        try {
            createFatJar(outputJar, files, progressLogger)
        } finally {
            progressLogger.completed()
        }
    }

    private fun createFatJar(outputJar: File, files: MutableCollection<out File>, progressLogger: ProgressLogger) {
        classpathBuilder.jar(outputJar, ClasspathBuilder.Action { builder: ClasspathBuilder.EntryBuilder? -> processFiles(builder!!, files, progressLogger) })
    }

    @Throws(IOException::class)
    private fun processFiles(builder: ClasspathBuilder.EntryBuilder, files: MutableCollection<out File>, progressLogger: ProgressLogger) {
        val progressFormatter = PercentageProgressFormatter("Generating", Iterables.size(files) + ADDITIONAL_PROGRESS_STEPS)

        val services: MutableMap<String, MutableList<String>> = LinkedHashMap<String, MutableList<String>>()
        val writer: MultiProducerSingleConsumerProcessor<InputFile> = createShadedJarWriter(builder, progressLogger, progressFormatter, services)

        writer.start()
        try {
            buildOperationExecutor.runAll<RunnableBuildOperation>(Action { queue: BuildOperationQueue<RunnableBuildOperation>? ->
                var index = 0
                for (file in files) {
                    val inputFile = InputFile(file, index++)
                    queue!!.add(object : RunnableBuildOperation {
                        @Throws(Exception::class)
                        override fun run(context: BuildOperationContext) {
                            classpathWalker.visit(inputFile.file, ClasspathEntryVisitor { entry: ClasspathEntryVisitor.Entry? -> processEntry(inputFile, entry!!) })
                            writer.submit(inputFile)
                        }

                        override fun description(): BuildOperationDescriptor.Builder {
                            return@runAll BuildOperationDescriptor.displayName("Visiting " + file.getName())
                        }
                    })
                }
            })
        } finally {
            writer.stop(Duration.ofMinutes(5))
        }

        writeServiceFiles(builder, services)
        progressLogger.progress(progressFormatter.incrementAndGetProgress())

        writeIdentifyingMarkerFile(builder)
        progressLogger.progress(progressFormatter.incrementAndGetProgress())
    }

    /**
     * The processed and remapped contents of a file that is to be included
     * in the relocated jar.
     */
    private class InputFile(private val file: File, val index: Int) : Comparable<InputFile> {
        private val names: MutableList<String>
        private val contents: MutableList<ByteArray>
        val services: MutableMap<String, MutableList<String>>

        init {
            this.names = ArrayList<String>()
            this.contents = ArrayList<ByteArray>()
            this.services = LinkedHashMap<String, MutableList<String>>()
        }

        fun addServiceProviders(serviceType: String, providers: MutableList<String>) {
            services.computeIfAbsent(serviceType) { k: String? -> ArrayList<String?>() }.addAll(providers)
        }

        /**
         * Put a new entry into the remapped result.
         */
        fun put(name: String, content: ByteArray) {
            names.add(name)
            contents.add(content)
        }

        @Throws(IOException::class)
        fun forEachEntry(consumer: EntryConsumer) {
            for (i in names.indices) {
                val name = names.get(i)
                val content = contents.get(i)
                consumer.accept(name, content)
            }
        }

        override fun compareTo(o: InputFile): Int {
            return Integer.compare(index, o.index)
        }

        internal interface EntryConsumer {
            @Throws(IOException::class)
            fun accept(name: String, content: ByteArray)
        }
    }

    @Throws(IOException::class)
    private fun writeServiceFiles(builder: ClasspathBuilder.EntryBuilder, services: MutableMap<String, MutableList<String>>) {
        for (service in services.entries) {
            val allProviders = Joiner.on("\n").join(service.value)
            builder.put(SERVICES_DIR_PREFIX + service.key, allProviders.toByteArray(StandardCharsets.UTF_8))
        }
    }

    @Throws(IOException::class)
    private fun writeIdentifyingMarkerFile(builder: ClasspathBuilder.EntryBuilder) {
        builder.put(GradleRuntimeShadedJarDetector.MARKER_FILENAME, ByteArray(0))
    }

    @Throws(IOException::class)
    private fun processEntry(builder: InputFile, entry: ClasspathEntryVisitor.Entry) {
        val name = entry.getName()
        if (name == "META-INF/MANIFEST.MF") {
            return
        }
        // Remove license files that cause collisions between a LICENSE file and a license/ directory.
        if (name.startsWith("LICENSE") || name.startsWith("license")) {
            return
        }

        if (name.endsWith(".class")) {
            processClassFile(builder, entry)
        } else if (name.startsWith(SERVICES_DIR_PREFIX)) {
            processServiceDescriptor(builder, entry)
        } else {
            processResource(builder, entry)
        }
    }

    @Throws(IOException::class)
    private fun processServiceDescriptor(inputFile: InputFile, entry: ClasspathEntryVisitor.Entry) {
        val name = entry.getName()
        val descriptorName: String = name.substring(SERVICES_DIR_PREFIX.length)
        val descriptorApiClass: String? = periodsToSlashes(descriptorName)[0]
        var relocatedApiClassName = remapper.maybeRelocateResource(descriptorApiClass)
        if (relocatedApiClassName == null) {
            relocatedApiClassName = descriptorApiClass
        }

        val bytes = entry.getContent()
        val content = String(bytes, StandardCharsets.UTF_8).replace("(?m)^#.*".toRegex(), "").trim { it <= ' ' }  // clean up comments and new lines

        val descriptorImplClasses: Array<String?> = periodsToSlashes(*separateLines(content))
        val relocatedImplClassNames: Array<String?> = maybeRelocateResources(*descriptorImplClasses)
        val serviceType = slashesToPeriods(relocatedApiClassName)[0]
        val serviceProviders = slashesToPeriods(*relocatedImplClassNames)

        inputFile.addServiceProviders(serviceType, Arrays.asList<String>(*serviceProviders))
    }

    private fun slashesToPeriods(vararg slashClassNames: String?): Array<String> {
        return Arrays.stream<String?>(slashClassNames).filter { obj: String? -> Objects.nonNull(obj) }
            .map<String> { clsName: String? -> clsName!!.replace('/', '.') }.map<String> { obj: String? -> obj!!.trim { it <= ' ' } }
            .toArray<String> { _Dummy_.__Array__() }
    }

    private fun periodsToSlashes(vararg periodClassNames: String?): Array<String?> {
        return Arrays.stream<String?>(periodClassNames).filter { obj: String? -> Objects.nonNull(obj) }
            .map<String> { clsName: String? -> clsName!!.replace('.', '/') }
            .toArray<String> { _Dummy_.__Array__() }
    }

    @Throws(IOException::class)
    private fun processResource(builder: InputFile, entry: ClasspathEntryVisitor.Entry) {
        val name = entry.getName()
        val resource = entry.getContent()

        val i = name.lastIndexOf("/")
        val path = if (i == -1) null else name.substring(0, i)

        if (remapper.keepOriginalResource(path)) {
            // we're writing 2 copies of the resource: one relocated, the other not, in order to support `getResource/getResourceAsStream` with
            // both absolute and relative paths
            builder.put(name, resource)
        }

        val remappedResourceName = if (path != null) remapper.maybeRelocateResource(path) else null
        if (remappedResourceName != null) {
            val newFileName = remappedResourceName + name.substring(i)
            builder.put(newFileName, resource)
        }
    }

    @Throws(IOException::class)
    private fun processClassFile(builder: InputFile, entry: ClasspathEntryVisitor.Entry) {
        val name = entry.getName()
        val className = name.substring(0, name.length - ".class".length)
        if (isModuleInfoClass(className)) {
            // do not include module-info files, as they would represent a bundled dependency module, instead of Gradle itself
            return
        }
        val bytes = entry.getContent()
        val remappedClass = remapClass(className, bytes)

        val remappedClassName = remapper.maybeRelocateResource(className)
        val newFileName = (if (remappedClassName == null) className else remappedClassName) + ".class"

        builder.put(newFileName, remappedClass)
    }

    private fun remapClass(className: String, bytes: ByteArray): ByteArray {
        val classReader = ClassReader(bytes)
        val classWriter = ClassWriter(0)
        val remappingVisitor: ClassVisitor = ShadingClassRemapper(classWriter, remapper)

        try {
            classReader.accept(remappingVisitor, ClassReader.EXPAND_FRAMES)
        } catch (e: Exception) {
            throw GradleException("Error in ASM processing class: " + className, e)
        }

        return classWriter.toByteArray()
    }

    private class ShadingClassRemapper(classWriter: ClassWriter, private val dependencyRelocator: ImplementationDependencyRelocator) : ClassRemapper(
        classWriter,
        dependencyRelocator
    ) {
        val remappedClassLiterals: MutableMap<String, String>

        init {
            remappedClassLiterals = HashMap<String, String>()
        }

        override fun visitField(access: Int, name: String, desc: String, signature: String, value: Any): FieldVisitor {
            var remapping: ImplementationDependencyRelocator.ClassLiteralRemapping? = null
            if (CLASS_DESC == desc) {
                remapping = dependencyRelocator.maybeRemap(name)
                if (remapping != null) {
                    remappedClassLiterals.put(remapping.getLiteral(), remapping.getLiteralReplacement().replace("/", "."))
                }
            }
            return super.visitField(access, if (remapping != null) remapping.getFieldNameReplacement() else name, desc, signature, value)
        }

        override fun visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array<String>): MethodVisitor {
            return object : MethodVisitor(AsmConstants.ASM_LEVEL, super.visitMethod(access, name, desc, signature, exceptions)) {
                override fun visitLdcInsn(cst: Any) {
                    if (cst is String) {
                        var literal = remappedClassLiterals.get(cst)
                        if (literal == null) {
                            // tries to relocate literals in the form of foo/bar/Bar
                            literal = dependencyRelocator.maybeRelocateResource(cst)
                        }
                        if (literal == null) {
                            // tries to relocate literals in the form of foo.bar.Bar
                            literal = dependencyRelocator.maybeRelocateResource(cst.replace('.', '/'))
                            if (literal != null) {
                                literal = literal.replace("/", ".")
                            }
                        }
                        super.visitLdcInsn(if (literal != null) literal else cst)
                    } else {
                        super.visitLdcInsn(cst)
                    }
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
                    if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) && CLASS_DESC == desc) {
                        val remapping = dependencyRelocator.maybeRemap(name)
                        if (remapping != null) {
                            super.visitFieldInsn(opcode, owner, remapping.getFieldNameReplacement(), desc)
                            return
                        }
                    }
                    super.visitFieldInsn(opcode, owner, name, desc)
                }
            }
        }
    }

    private fun maybeRelocateResources(vararg resources: String?): Array<String?> {
        return Arrays.stream<String?>(resources)
            .filter { obj: String? -> Objects.nonNull(obj) }
            .map<String?> { resource: String? ->
                val remapped = remapper.maybeRelocateResource(resource)
                if (remapped == null) {
                    return@map resource // This resource was not relocated. Use the original name.
                }
                remapped
            }
            .toArray<String> { _Dummy_.__Array__() }
    }

    private fun separateLines(entry: String): Array<String?> {
        return entry.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    companion object {
        private const val ADDITIONAL_PROGRESS_STEPS = 2
        private const val SERVICES_DIR_PREFIX = "META-INF/services/"
        private const val CLASS_DESC = "Ljava/lang/Class;"

        private val LOGGER: Logger = LoggerFactory.getLogger(RuntimeShadedJarCreator::class.java)

        private fun createShadedJarWriter(
            builder: ClasspathBuilder.EntryBuilder,
            progressLogger: ProgressLogger,
            progressFormatter: PercentageProgressFormatter,
            services: MutableMap<String, MutableList<String>>
        ): MultiProducerSingleConsumerProcessor<InputFile> {
            return MultiProducerSingleConsumerProcessor<InputFile>("shaded jar writer", object : Consumer<InputFile> {
                private var index = 0
                private val allProcessedFiles = PriorityQueue<InputFile>()
                private val seenPaths: MutableSet<String> = HashSet<String>()

                override fun accept(processedFile: InputFile) {
                    allProcessedFiles.add(processedFile)

                    var toProcess: InputFile?
                    while (!allProcessedFiles.isEmpty() && (allProcessedFiles.peek().also { toProcess = it }).index == index) {
                        try {
                            progressLogger.progress(progressFormatter.getProgress())
                            toProcess!!.forEachEntry(InputFile.EntryConsumer { name: String, content: ByteArray ->
                                if (seenPaths.add(name)) {
                                    builder.put(name, content)
                                }
                            })
                            for (entry in toProcess.services.entries) {
                                services.computeIfAbsent(entry.key) { k: String? -> ArrayList<String?>() }.addAll(entry.value)
                            }
                            progressFormatter.increment()
                            index++
                            allProcessedFiles.poll()
                        } catch (e: IOException) {
                            throw RuntimeException("Failed to write shaded jar", e)
                        }
                    }
                }
            })
        }

        private fun isModuleInfoClass(name: String): Boolean {
            return "module-info" == name
        }
    }
}
