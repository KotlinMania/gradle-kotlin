/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.jvm.inspection

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import org.gradle.api.GradleException
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class MetadataProbe {
    private val probeClass = Suppliers.memoize<ByteArray?>(Supplier { createProbeClass() })

    fun writeClass(outputDirectory: File?): File {
        val probeFile = File(outputDirectory, PROBE_CLASS_NAME + ".class")
        try {
            IoActions.withResource<FileOutputStream?>(FileOutputStream(probeFile), object : ErroringAction<FileOutputStream?>() {
                @Throws(Exception::class)
                override fun doExecute(thing: FileOutputStream) {
                    thing.write(probeClass.get())
                }
            })
        } catch (e: FileNotFoundException) {
            throw GradleException("Unable to write Java probe file", e)
        }
        return probeFile
    }

    companion object {
        const val PROBE_CLASS_NAME: String = "JavaProbe"
        const val MARKER_PREFIX: String = "GRADLE_PROBE_VALUE:"

        private fun createProbeClass(): ByteArray? {
            val cw = ClassWriter(0)
            createClassHeader(cw)
            createConstructor(cw)
            createMainMethod(cw)
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun createClassHeader(cw: ClassWriter) {
            cw.visit(Opcodes.V1_1, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, PROBE_CLASS_NAME, null, "java/lang/Object", null)
        }

        private fun createMainMethod(cw: ClassWriter) {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null)
            mv.visitCode()
            val l0 = Label()
            mv.visitLabel(l0)
            for (type in ProbedSystemProperty.entries) {
                if (type != ProbedSystemProperty.Z_ERROR) {
                    dumpProperty(mv, type.getSystemPropertyKey())
                }
            }
            mv.visitInsn(Opcodes.RETURN)
            val l3 = Label()
            mv.visitLabel(l3)
            mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l3, 0)
            mv.visitMaxs(3, 1)
            mv.visitEnd()
        }

        private fun dumpProperty(mv: MethodVisitor, property: String?) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
            mv.visitLdcInsn(MARKER_PREFIX)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false)

            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
            mv.visitLdcInsn(property)
            mv.visitLdcInsn("unknown")
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
        }

        private fun createConstructor(cw: ClassWriter) {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            mv.visitCode()
            val l0 = Label()
            mv.visitLabel(l0)
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitInsn(Opcodes.RETURN)
            val l1 = Label()
            mv.visitLabel(l1)
            mv.visitLocalVariable("this", "LJavaProbe;", null, l0, l1, 0)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }
    }
}
