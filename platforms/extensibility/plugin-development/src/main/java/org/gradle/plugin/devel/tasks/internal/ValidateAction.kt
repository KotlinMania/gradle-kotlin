/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.plugin.devel.tasks.internal

import com.google.common.io.Files
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.reflect.validation.TypeAwareProblemBuilder
import org.gradle.model.internal.asm.AsmConstants
import org.gradle.util.internal.TextUtil
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import javax.inject.Inject

abstract class ValidateAction : WorkAction<ValidateAction.Params> {
    interface Params : WorkParameters {
        val classes: ConfigurableFileCollection?

        val outputFile: RegularFileProperty?

        val enableStricterValidation: Property<Boolean>?
    }

    @get:Inject
    abstract val problems: ProblemsInternal?

    override fun execute() {
        val taskValidationWarnings: MutableList<Problem> = ArrayList<Problem>()
        val taskValidationErrors: MutableList<Problem> = ArrayList<Problem>()

        val params = getParameters()

        params.classes.getAsFileTree().visit(ValidationProblemCollector(taskValidationWarnings, taskValidationErrors, params, this.problems))
        storeResults(taskValidationWarnings, taskValidationErrors, params.outputFile)
    }

    private class TaskNameCollectorVisitor(private val classNames: MutableCollection<String>) : ClassVisitor(AsmConstants.ASM_LEVEL) {
        override fun visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array<String>) {
            if ((access and Opcodes.ACC_PUBLIC) != 0) {
                classNames.add(name.replace('/', '.'))
            }
        }
    }

    private class ValidationProblemCollector(
        private val taskValidationWarnings: MutableList<Problem>,
        private val taskValidationErrors: MutableList<Problem>,
        private val params: Params,
        private val problems: ProblemsInternal
    ) : EmptyFileVisitor() {
        private val classLoader: ClassLoader

        init {
            this.classLoader = Thread.currentThread().getContextClassLoader()
        }

        override fun visitFile(fileDetails: FileVisitDetails) {
            if (!fileDetails.getPath().endsWith(".class")) {
                return
            }
            val classNames: MutableList<String> = getClassNames(fileDetails)
            for (className in classNames) {
                val clazz: Class<*>
                try {
                    clazz = classLoader.loadClass(className)
                } catch (e: IncompatibleClassChangeError) {
                    LOGGER.debug("Could not load class: " + className, e)
                    continue
                } catch (e: NoClassDefFoundError) {
                    LOGGER.debug("Could not load class: " + className, e)
                    continue
                } catch (e: VerifyError) {
                    LOGGER.debug("Could not load class: " + className, e)
                    continue
                } catch (e: ClassNotFoundException) {
                    LOGGER.debug("Could not load class: " + className, e)
                    continue
                }
                Companion.collectValidationProblems(clazz, taskValidationWarnings, taskValidationErrors, params.enableStricterValidation.get(), problems)
            }
        }

        companion object {
            private fun collectValidationProblems(
                topLevelBean: Class<*>,
                taskValidationWarnings: MutableList<Problem>,
                taskValidationErrors: MutableList<Problem>,
                enableStricterValidation: Boolean,
                problems: ProblemsInternal
            ) {
                val validationContext: DefaultTypeValidationContext = createTypeValidationContext(topLevelBean, enableStricterValidation, problems)
                PropertyValidationAccess.Companion.collectValidationProblems(topLevelBean, validationContext)

                taskValidationWarnings.addAll(validationContext.getWarnings())
                taskValidationErrors.addAll(validationContext.getErrors())
            }

            private fun createTypeValidationContext(topLevelBean: Class<*>, enableStricterValidation: Boolean, problems: ProblemsInternal): DefaultTypeValidationContext {
                if (Task::class.java.isAssignableFrom(topLevelBean)) {
                    return createValidationContextAndValidateCacheableAnnotations(topLevelBean, CacheableTask::class.java, enableStricterValidation, problems)
                }
                if (TransformAction::class.java.isAssignableFrom(topLevelBean)) {
                    return createValidationContextAndValidateCacheableAnnotations(topLevelBean, CacheableTransform::class.java, enableStricterValidation, problems)
                }
                return createValidationContext(topLevelBean, enableStricterValidation, problems)
            }

            private fun createValidationContextAndValidateCacheableAnnotations(
                topLevelBean: Class<*>,
                cacheableAnnotationClass: Class<out Annotation>,
                enableStricterValidation: Boolean,
                problems: ProblemsInternal
            ): DefaultTypeValidationContext {
                val cacheable = topLevelBean.isAnnotationPresent(cacheableAnnotationClass)
                val validationContext: DefaultTypeValidationContext = createValidationContext(topLevelBean, cacheable || enableStricterValidation, problems)
                if (enableStricterValidation) {
                    validateCacheabilityAnnotationPresent(topLevelBean, cacheable, cacheableAnnotationClass, validationContext)
                }
                return validationContext
            }

            private fun createValidationContext(topLevelBean: Class<*>, reportCacheabilityProblems: Boolean, problems: ProblemsInternal): DefaultTypeValidationContext {
                return DefaultTypeValidationContext.withRootType(topLevelBean, reportCacheabilityProblems, problems)
            }

            private fun validateCacheabilityAnnotationPresent(
                topLevelBean: Class<*>,
                cacheable: Boolean,
                cacheableAnnotationClass: Class<out Annotation>,
                validationContext: DefaultTypeValidationContext
            ) {
                if (topLevelBean.isInterface()) {
                    // Won't validate interfaces
                    return
                }
                if (!cacheable && topLevelBean.getAnnotation<DisableCachingByDefault>(DisableCachingByDefault::class.java) == null && topLevelBean.getAnnotation<UntrackedTask>(UntrackedTask::class.java) == null
                ) {
                    val isTask = Task::class.java.isAssignableFrom(topLevelBean)
                    val cacheableAnnotation = "@" + cacheableAnnotationClass.getSimpleName()
                    val disableCachingAnnotation = "@" + DisableCachingByDefault::class.java.getSimpleName()
                    val untrackedTaskAnnotation = "@" + UntrackedTask::class.java.getSimpleName()
                    val workType = if (isTask) "task" else "transform action"
                    validationContext.visitTypeError(Action { problem: TypeAwareProblemBuilder? ->
                        val builder: ProblemSpec = problem!!
                            .withAnnotationType(topLevelBean)
                            .id(TextUtil.screamingSnakeToKebabCase(ValidationTypes.NOT_CACHEABLE_WITHOUT_REASON), "Not cacheable without reason", GradleCoreProblemGroup.validation().type())
                            .contextualLabel("must be annotated either with " + cacheableAnnotation + " or with " + disableCachingAnnotation)
                            .documentedAt(userManual("validation_problems", "disable_caching_by_default"))
                            .details("The " + workType + " author should make clear why a " + workType + " is not cacheable")
                            .solution("Add " + disableCachingAnnotation + "(because = ...)")
                            .solution("Add " + cacheableAnnotation)
                        if (isTask) {
                            builder.solution("Add " + untrackedTaskAnnotation + "(because = ...)")
                        }
                    }
                    )
                }
            }

            private fun getClassNames(fileDetails: FileVisitDetails): MutableList<String> {
                val reader: ClassReader = createClassReader(fileDetails)
                val classNames: MutableList<String> = ArrayList<String>()
                reader.accept(TaskNameCollectorVisitor(classNames), ClassReader.SKIP_CODE)
                return classNames
            }

            private fun createClassReader(fileDetails: FileVisitDetails): ClassReader {
                try {
                    return ClassReader(Files.asByteSource(fileDetails.getFile()).read())
                } catch (e: IOException) {
                    throw throwAsUncheckedException(e)
                }
            }
        }
    }

    companion object {
        private val LOGGER: Logger = getLogger(ValidateAction::class.java)!!

        private fun storeResults(warnings: MutableList<Problem>, errors: MutableList<Problem>, outputFile: RegularFileProperty) {
            if (outputFile.isPresent()) {
                val output = outputFile.get().getAsFile()
                try {
                    output.createNewFile()
                    Files.asCharSink(output, StandardCharsets.UTF_8).write(ValidationProblemSerialization.serialize(warnings, errors))
                } catch (ex: IOException) {
                    throw UncheckedIOException(ex)
                }
            }
        }
    }
}
