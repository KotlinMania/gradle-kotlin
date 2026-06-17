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

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.gradle.internal.classloader.TransformingClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type

/**
 * Transforms @GroovyASTTransformationClass(classes = {classLiterals}) into @GroovyASTTransformationClass([classNames]),
 * to work around GROOVY-5416.
 */
internal class GroovyCompileTransformingClassLoader(parent: ClassLoader, classPath: ClassPath) : TransformingClassLoader("groovy-compile-transforming-loader", parent, classPath) {
    override fun transform(className: String, bytes: ByteArray): ByteArray {
        // First scan for annotation, and short circuit transformation if not present
        var bytes = bytes
        val classReader = ClassReader(bytes)

        val detector = AnnotationDetector()
        classReader.accept(detector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_CODE)
        if (!detector.found) {
            return bytes
        }

        val classWriter = ClassWriter(0)
        classReader.accept(TransformingAdapter(classWriter), 0)
        bytes = classWriter.toByteArray()
        return bytes
    }

    private class AnnotationDetector : ClassVisitor(AsmConstants.ASM_LEVEL) {
        private var found = false

        override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
            if (desc == ANNOTATION_DESCRIPTOR) {
                found = true
            }
            return null
        }
    }

    private class TransformingAdapter(classWriter: ClassWriter?) : ClassVisitor(AsmConstants.ASM_LEVEL, classWriter) {
        override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
            if (desc == ANNOTATION_DESCRIPTOR) {
                return AnnotationTransformingVisitor(super.visitAnnotation(desc, visible))
            }
            return super.visitAnnotation(desc, visible)
        }

        private class AnnotationTransformingVisitor(annotationVisitor: AnnotationVisitor?) : AnnotationVisitor(AsmConstants.ASM_LEVEL, annotationVisitor) {
            private val names: MutableList<String?> = ArrayList<String?>()

            override fun visitArray(name: String): AnnotationVisitor? {
                if (name == "classes") {
                    return object : AnnotationVisitor(AsmConstants.ASM_LEVEL) {
                        override fun visit(name: String?, value: Any?) {
                            val type = value as Type
                            names.add(type.getClassName())
                        }
                    }
                } else if (name == "value") {
                    return object : AnnotationVisitor(AsmConstants.ASM_LEVEL) {
                        override fun visit(name: String?, value: Any?) {
                            val type = value as String?
                            names.add(type)
                        }
                    }
                } else {
                    return super.visitArray(name)
                }
            }

            override fun visitEnd() {
                if (!names.isEmpty()) {
                    val visitor = super.visitArray("value")
                    for (name in names) {
                        visitor.visit(null, name)
                    }
                    visitor.visitEnd()
                }
                super.visitEnd()
            }
        }
    }

    companion object {
        private val ANNOTATION_DESCRIPTOR: String = Type.getType(GroovyASTTransformationClass::class.java).getDescriptor()

        init {
            try {
                registerAsParallelCapable()
            } catch (ignore: NoSuchMethodError) {
                // Not supported on Java 6
            }
        }
    }
}
