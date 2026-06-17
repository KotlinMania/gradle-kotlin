/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.processor

import org.gradle.internal.Cast
import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation
import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.processor.codegen.CompositeInstrumentationCodeGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGeneratorHost
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension
import org.gradle.internal.instrumentation.processor.extensibility.ClassLevelAnnotationsContributor
import org.gradle.internal.instrumentation.processor.extensibility.CodeGeneratorContributor
import org.gradle.internal.instrumentation.processor.extensibility.InstrumentationProcessorExtension
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension
import org.gradle.internal.instrumentation.processor.extensibility.ResourceGeneratorContributor
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.ReadRequestContext
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.InvalidRequest
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findAnnotationMirror
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findAnnotationValue
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementScanner8
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

abstract class AbstractInstrumentationProcessor : AbstractProcessor() {
    protected abstract val extensions: MutableCollection<InstrumentationProcessorExtension?>?

    override fun getSupportedOptions(): MutableSet<String?> {
        return HashSet<String?>(Arrays.asList<String?>("org.gradle.annotation.processing.aggregating", PROJECT_NAME_OPTIONS))
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String?> {
        return this.supportedAnnotations.stream().map<String?> { obj: Class<out Annotation?>? -> obj!!.getName() }.collect(Collectors.toSet())
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    private val supportedAnnotations: MutableSet<Class<out Annotation?>?>
        get() = getExtensionsByType<ClassLevelAnnotationsContributor?>(ClassLevelAnnotationsContributor::class.java).stream()
            .flatMap<Class<out Annotation?>?> { it: ClassLevelAnnotationsContributor? -> it!!.contributeClassLevelAnnotationTypes()!!.stream() }
            .collect(Collectors.toSet())

    override fun process(annotations: MutableSet<out TypeElement?>?, roundEnv: RoundEnvironment): Boolean {
        // GetAnnotatedElementsSkippingPackageRoots() is the same as roundEnv.getElementsAnnotatedWith() but skips package roots.
        // See issue: https://github.com/gradle/gradle/issues/29926
        val annotatedTypes: Stream<out Element?>? = getAnnotatedElementsSkippingPackageRoots(roundEnv, this.supportedAnnotations)
            .flatMap<Element?> { element: Element? -> findActualTypesToVisit(element!!).stream() }
            .sorted(Comparator.comparing<Element?, String?>(Function { obj: Element? -> obj.elementQualifiedName() }))
        collectAndProcessRequests(annotatedTypes)
        return false
    }

    private fun findActualTypesToVisit(typeElement: Element): MutableSet<Element?> {
        val annotationMirror = findAnnotationMirror(typeElement, VisitForInstrumentation::class.java)
        if (!annotationMirror.isPresent()) {
            return mutableSetOf<Element?>(typeElement)
        }

        val values = findAnnotationValue(annotationMirror.get(), "value")
            .orElseThrow<IllegalStateException?>(Supplier { IllegalStateException("missing annotation value") })
            .getValue() as MutableList<AnnotationValue?>
        return values.stream()
            .map<Element?> { v: AnnotationValue? -> processingEnv.getTypeUtils().asElement(v!!.getValue() as TypeMirror?) }
            .collect(Collectors.toSet())
    }

    private fun <T : InstrumentationProcessorExtension?> getExtensionsByType(type: Class<T?>): MutableCollection<T?> {
        return Cast.uncheckedCast<MutableCollection<T?>>(this.extensions!!.stream().filter { obj: InstrumentationProcessorExtension? -> type.isInstance(obj) }.collect(Collectors.toList()))!!
    }

    private fun collectAndProcessRequests(annotatedElements: Stream<out Element?>?) {
        val readers = getExtensionsByType<AnnotatedMethodReaderExtension?>(AnnotatedMethodReaderExtension::class.java)

        val allMethodElementsInAnnotatedClasses: MutableList<ExecutableElement?> = getExecutableElementsFromElements(annotatedElements)

        val errors: MutableMap<ExecutableElement?, MutableList<InvalidRequest?>?> = LinkedHashMap<ExecutableElement?, MutableList<InvalidRequest?>?>()
        val successResults: MutableList<CallInterceptionRequestReader.Result.Success?> = ArrayList<CallInterceptionRequestReader.Result.Success?>()
        readRequests(readers, allMethodElementsInAnnotatedClasses, errors, successResults)

        if (!errors.isEmpty()) {
            val messager = processingEnv.getMessager()
            errors.forEach { (element: ExecutableElement?, elementErrors: MutableList<InvalidRequest?>?) ->
                elementErrors!!.forEach(Consumer { error: InvalidRequest? ->
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        error!!.reason,
                        element
                    )
                })
            }
            return
        }

        val requests = postProcessRequests(successResults)

        runCodeGeneration(requests)
    }

    private fun postProcessRequests(successResults: MutableList<CallInterceptionRequestReader.Result.Success?>): MutableList<CallInterceptionRequest?> {
        var requests: MutableList<CallInterceptionRequest?> = successResults.stream().map<Any?>(CallInterceptionRequestReader.Result.Success::getRequest).collect(Collectors.toList())
        for (postProcessor in getExtensionsByType<RequestPostProcessorExtension>(RequestPostProcessorExtension::class.java)) {
            requests = requests.stream().flatMap<CallInterceptionRequest?> { request: CallInterceptionRequest? -> postProcessor!!.postProcessRequest(request)!!.stream() }.collect(Collectors.toList())
        }
        return requests
    }

    private fun runCodeGeneration(requests: MutableList<CallInterceptionRequest?>?) {
        val generatorHost = InstrumentationCodeGeneratorHost(
            processingEnv.getFiler(),
            processingEnv.getMessager(),
            CompositeInstrumentationCodeGenerator(
                getExtensionsByType<CodeGeneratorContributor?>(CodeGeneratorContributor::class.java).stream()
                    .map<InstrumentationCodeGenerator?> { obj: CodeGeneratorContributor? -> obj!!.contributeCodeGenerator() }.collect(
                        Collectors.toList()
                    )
            ),
            getExtensionsByType<ResourceGeneratorContributor?>(ResourceGeneratorContributor::class.java).stream()
                .map<InstrumentationResourceGenerator?> { obj: ResourceGeneratorContributor? -> obj!!.contributeResourceGenerator() }.collect(
                    Collectors.toList()
                )
        )

        generatorHost.generateCodeForRequestedInterceptors(requests)
    }

    /**
     * Discover all elements annotated with the given annotations, skipping package roots.
     * This is similar to [RoundEnvironment.getElementsAnnotatedWith], but skips package roots.
     * Since if package-info.java exist, we could discover types from other projects in the same package.
     *
     * See issue: https://github.com/gradle/gradle/issues/29926
     */
    private fun getAnnotatedElementsSkippingPackageRoots(
        roundEnvironment: RoundEnvironment,
        annotations: MutableSet<Class<out Annotation?>?>
    ): Stream<out Element?> {
        val annotationsAsElements: MutableSet<TypeElement?> = annotations.stream()
            .filter { annotation: Class<out Annotation?>? -> annotation!!.getCanonicalName() != null }
            .map<TypeElement?> { annotation: Class<out Annotation?>? -> processingEnv.getElementUtils().getTypeElement(annotation!!.getCanonicalName()) }
            .collect(Collectors.toCollection(Supplier { LinkedHashSet<TypeElement?>(annotations.size) }))

        var result = mutableSetOf<Element?>()
        val scanner = AnnotationScanner(processingEnv.getElementUtils())
        for (element in roundEnvironment.getRootElements()) {
            if (element !is PackageElement) {
                result = scanner.scan(element, annotationsAsElements)
            }
        }
        return result.stream()
    }

    private class AnnotationScanner(private val elements: Elements) : ElementScanner8<MutableSet<Element?>?, MutableSet<TypeElement?>?>(mutableSetOf<Element?>()) {
        private val annotatedElements: MutableSet<Element?> = LinkedHashSet<Element?>()

        override fun scan(e: Element, annotations: MutableSet<TypeElement?>): MutableSet<Element?> {
            for (annotationMirror in elements.getAllAnnotationMirrors(e)) {
                if (annotations.contains(annotationMirror.getAnnotationType().asElement() as TypeElement?)) {
                    annotatedElements.add(e)
                    break
                }
            }
            e.accept<MutableSet<Element?>?, MutableSet<TypeElement?>?>(this, annotations)
            return annotatedElements
        }

        override fun visitType(e: TypeElement, p: MutableSet<TypeElement?>?): MutableSet<Element?>? {
            // Type parameters are not considered to be enclosed by a type
            scan(e.getTypeParameters(), p)
            return super.visitType(e, p)
        }

        override fun visitExecutable(e: ExecutableElement, p: MutableSet<TypeElement?>?): MutableSet<Element?>? {
            // Type parameters are not considered to be enclosed by an executable
            scan(e.getTypeParameters(), p)
            return super.visitExecutable(e, p)
        }
    }

    companion object {
        const val PROJECT_NAME_OPTIONS: String = "org.gradle.annotation.processing.instrumented.project"

        private fun readRequests(
            readers: MutableCollection<AnnotatedMethodReaderExtension>,
            allMethodElementsInAnnotatedClasses: MutableList<ExecutableElement?>,
            errors: MutableMap<ExecutableElement?, MutableList<InvalidRequest?>?>,
            successResults: MutableList<CallInterceptionRequestReader.Result.Success?>
        ) {
            val context = ReadRequestContext()
            for (methodElement in allMethodElementsInAnnotatedClasses) {
                for (reader in readers) {
                    val readerResults = reader.readRequest(methodElement, context)
                    for (readerResult in readerResults!!) {
                        if (readerResult is InvalidRequest) {
                            errors.computeIfAbsent(methodElement) { key: javax.lang.model.element.ExecutableElement? -> java.util.ArrayList<InvalidRequest?>() }!!.add(readerResult)
                        } else {
                            successResults.add(readerResult as CallInterceptionRequestReader.Result.Success?)
                        }
                    }
                }
            }
        }
    }
}
