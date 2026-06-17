/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ModuleVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Post processes the compilation result to add the ModuleMainClass attribute to module-info.class
 */
class ModuleApplicationNameWritingCompiler<T : JavaCompileSpec?>(private val delegate: Compiler<T?>) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult? {
        val result = delegate.execute(spec)
        val mainClass = spec!!.compileOptions!!.javaModuleMainClass
        if (mainClass != null) {
            val moduleInfo = File(spec.getDestinationDir(), "module-info.class")
            if (moduleInfo.exists()) {
                addMainClass(moduleInfo, mainClass)
            }
        }
        return result
    }

    private class ModuleInfoVisitor(private val mainClass: String, cv: ClassVisitor?) : ClassVisitor(Opcodes.ASM4, cv) {
        override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor {
            return ModuleMainClassWriter(mainClass, cv.visitModule(name, access, version))
        }
    }

    private class ModuleMainClassWriter(private val mainClass: String, mv: ModuleVisitor?) : ModuleVisitor(Opcodes.ASM4, mv) {
        override fun visitEnd() {
            mv.visitMainClass(this.mainClass.replace('.', '/'))
            super.visitEnd()
        }
    }

    companion object {
        private fun addMainClass(moduleInfo: File, mainClass: String) {
            try {
                FileInputStream(moduleInfo).use { inputStream ->
                    val classReader = ClassReader(inputStream)
                    val classWriter = ClassWriter(classReader, 0)
                    val moduleInfoVisitor: ClassVisitor = ModuleInfoVisitor(mainClass, classWriter)
                    classReader.accept(moduleInfoVisitor, 0)
                    val out = FileOutputStream(moduleInfo)
                    out.write(classWriter.toByteArray())
                    out.close()
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
