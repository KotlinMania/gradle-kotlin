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
package org.gradle.api.internal.tasks.compile.incremental.asm

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.initialization.transform.utils.ClassAnalysisUtils
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.ModuleVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.lang.annotation.RetentionPolicy
import java.util.function.Consumer
import java.util.function.Predicate

class ClassDependenciesVisitor private constructor(private val typeFilter: Predicate<String?>, reader: ClassReader, private val interner: StringInterner) : ClassVisitor(API) {
    val constants: IntSet
    val privateClassDependencies: MutableSet<String?>
    val accessibleClassDependencies: MutableSet<String?>
    private var isAnnotationType = false
    var dependencyToAllReason: String? = null
        private set
    private var moduleName: String? = null
    private val retentionPolicyVisitor: RetentionPolicyVisitor

    init {
        this.constants = IntOpenHashSet(2)
        this.privateClassDependencies = HashSet<String?>()
        this.accessibleClassDependencies = HashSet<String?>()
        this.retentionPolicyVisitor = ClassDependenciesVisitor.RetentionPolicyVisitor()
        collectRemainingClassDependencies(reader)
    }

    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<String>) {
        isAnnotationType = isAnnotationType(interfaces)
        val types = if (isAccessible(access)) this.accessibleClassDependencies else this.privateClassDependencies
        maybeAddClassTypesFromSignature(signature, types)
        if (superName != null) {
            // superName can be null if what we are analyzing is `java.lang.Object`
            // which can happen when a custom Java SDK is on classpath (typically, android.jar)
            val type = Type.getObjectType(superName)
            maybeAddDependentType(types, type)
        }
        for (s in interfaces) {
            val interfaceType = Type.getObjectType(s)
            maybeAddDependentType(types, interfaceType)
        }
    }

    override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor? {
        moduleName = name
        dependencyToAllReason = "module-info of '" + name + "' has changed"
        return null
    }

    // performs a fast analysis of classes referenced in bytecode (method bodies)
    // avoiding us to implement a costly visitor and potentially missing edge cases
    private fun collectRemainingClassDependencies(reader: ClassReader) {
        ClassAnalysisUtils.getClassDependencies(reader, Consumer { classDescriptor: String? ->
            val type = Type.getObjectType(classDescriptor)
            maybeAddDependentType(this.privateClassDependencies, type)
        })
    }

    private fun maybeAddClassTypesFromSignature(signature: String?, types: MutableSet<String?>) {
        if (signature != null) {
            val signatureReader = SignatureReader(signature)
            signatureReader.accept(object : SignatureVisitor(API) {
                override fun visitClassType(className: String) {
                    val type = Type.getObjectType(className)
                    maybeAddDependentType(types, type)
                }
            })
        }
    }

    protected fun maybeAddDependentType(types: MutableSet<String?>, type: Type) {
        var type = type
        while (type.getSort() == Type.ARRAY) {
            type = type.getElementType()
        }
        if (type.getSort() != Type.OBJECT) {
            return
        }
        val name = type.getClassName()
        if (typeFilter.test(name)) {
            types.add(interner.intern(name!!))
        }
    }

    private fun isAnnotationType(interfaces: Array<String>): Boolean {
        return interfaces.size == 1 && interfaces[0] == "java/lang/annotation/Annotation"
    }

    override fun visitField(access: Int, name: String?, desc: String, signature: String?, value: Any?): FieldVisitor {
        val types = if (isAccessible(access)) this.accessibleClassDependencies else this.privateClassDependencies
        maybeAddClassTypesFromSignature(signature, types)
        maybeAddDependentType(types, Type.getType(desc))
        if (isAccessibleConstant(access, value)) {
            // we need to compute a hash for a constant, which is based on the name of the constant + its value
            // otherwise we miss the case where a class defines several constants with the same value, or when
            // two values are switched
            constants.add((name + '|' + value).hashCode()) //non-private const
        }
        return ClassDependenciesVisitor.FieldVisitor(types)
    }

    override fun visitMethod(access: Int, name: String?, desc: String, signature: String?, exceptions: Array<String?>?): MethodVisitor {
        val types = if (isAccessible(access)) this.accessibleClassDependencies else this.privateClassDependencies
        maybeAddClassTypesFromSignature(signature, types)
        addTypesFromMethodDescriptor(types, desc)
        return ClassDependenciesVisitor.MethodVisitor(types)
    }

    private fun addTypesFromMethodDescriptor(types: MutableSet<String?>, desc: String) {
        val methodType = Type.getMethodType(desc)
        maybeAddDependentType(types, methodType.getReturnType())
        for (argType in methodType.getArgumentTypes()) {
            maybeAddDependentType(types, argType)
        }
    }

    override fun visitAnnotation(desc: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
        if (isAnnotationType && "Ljava/lang/annotation/Retention;" == desc) {
            return retentionPolicyVisitor
        } else {
            maybeAddDependentType(this.accessibleClassDependencies, Type.getType(desc))
            return ClassDependenciesVisitor.AnnotationVisitor(this.accessibleClassDependencies)
        }
    }

    private inner class FieldVisitor(private val types: MutableSet<String?>) : org.objectweb.asm.FieldVisitor(API) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor {
            maybeAddDependentType(types, Type.getType(descriptor))
            return ClassDependenciesVisitor.AnnotationVisitor(types)
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor {
            maybeAddDependentType(types, Type.getType(descriptor))
            return ClassDependenciesVisitor.AnnotationVisitor(types)
        }
    }

    private inner class MethodVisitor(private val types: MutableSet<String?>) : org.objectweb.asm.MethodVisitor(API) {
        override fun visitLocalVariable(name: String?, desc: String, signature: String?, start: Label?, end: Label?, index: Int) {
            maybeAddClassTypesFromSignature(signature, this.privateClassDependencies)
            maybeAddDependentType(this.privateClassDependencies, Type.getType(desc))
            super.visitLocalVariable(name, desc, signature, start, end, index)
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor {
            maybeAddDependentType(types, Type.getType(descriptor))
            return ClassDependenciesVisitor.AnnotationVisitor(types)
        }

        override fun visitParameterAnnotation(parameter: Int, descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor {
            maybeAddDependentType(types, Type.getType(descriptor))
            return ClassDependenciesVisitor.AnnotationVisitor(types)
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean): org.objectweb.asm.AnnotationVisitor {
            maybeAddDependentType(types, Type.getType(descriptor))
            return ClassDependenciesVisitor.AnnotationVisitor(types)
        }

        override fun visitInvokeDynamicInsn(name: String?, descriptor: String, bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any?) {
            if (tryHandleSpecialBootstrapMethod(BootstrapMethod.Companion.fromIndy(bootstrapMethodHandle, *bootstrapMethodArguments))) {
                return
            }
            addTypesFromMethodDescriptor(this.privateClassDependencies, descriptor)
            maybeAddDependentType(this.privateClassDependencies, Type.getObjectType(bootstrapMethodHandle.getOwner()))

            for (arg in bootstrapMethodArguments) {
                addDependentTypeFromBootstrapMethodArgument(arg)
            }
        }

        override fun visitLdcInsn(value: Any?) {
            if (value is ConstantDynamic) {
                addDependentTypesFromConstantDynamic(value)
            }
        }

        fun addDependentTypesFromConstantDynamic(arg: ConstantDynamic) {
            if (tryHandleSpecialBootstrapMethod(BootstrapMethod.Companion.fromConstantDynamic(arg))) {
                return
            }
            maybeAddDependentType(this.privateClassDependencies, Type.getObjectType(arg.getBootstrapMethod().getOwner()))

            for (i in 0..<arg.getBootstrapMethodArgumentCount()) {
                addDependentTypeFromBootstrapMethodArgument(arg.getBootstrapMethodArgument(i))
            }
        }

        fun addDependentTypeFromBootstrapMethodArgument(arg: Any?) {
            if (arg is Type) {
                maybeAddDependentType(this.privateClassDependencies, arg)
            } else if (arg is Handle) {
                maybeAddDependentType(this.privateClassDependencies, Type.getObjectType(arg.getOwner()))
            } else if (arg is ConstantDynamic) {
                addDependentTypesFromConstantDynamic(arg)
            }
        }

        /**
         * Some bootstrap methods describe a dependency on a class, despite not containing a class reference in their
         * arguments. One way this can happen is with qualified enums in a switch expression, where they will bootstrap
         * a class constant using [java.lang.constant.ClassDesc.of]. The string represents a class name
         * which must be a dependency of the class being analyzed.
         *
         * @param bootstrapMethod the bootstrap method to check
         * @return if the bootstrap method was handled and its types added to the dependency set
         */
        fun tryHandleSpecialBootstrapMethod(bootstrapMethod: BootstrapMethod): Boolean {
            // Currently this method only handles the ClassDesc#of case, but there may be others in the future.
            // If so, this code should be refactored out to its own method.
            if (bootstrapMethod.getHandle() != CONSTANT_BOOTSTRAPS_INVOKE) {
                return false
            }
            if (bootstrapMethod.getArguments().size != 2) {
                return false
            }
            if (CLASS_DESC_OF != bootstrapMethod.getArguments().get(0)) {
                return false
            }
            val className = bootstrapMethod.getArguments().get(1)
            if (className !is String) {
                return false
            }
            maybeAddDependentType(this.privateClassDependencies, Type.getObjectType(className.replace('.', '/')))
            return true
        }
    }

    private inner class RetentionPolicyVisitor : org.objectweb.asm.AnnotationVisitor(API) {
        override fun visitEnum(name: String?, desc: String?, value: String?) {
            if ("Ljava/lang/annotation/RetentionPolicy;" == desc) {
                val policy = RetentionPolicy.valueOf(value!!)
                if (policy == RetentionPolicy.SOURCE) {
                    dependencyToAllReason = "source retention annotation '" + name + "' has changed"
                }
            }
        }
    }

    private inner class AnnotationVisitor(private val types: MutableSet<String?>) : org.objectweb.asm.AnnotationVisitor(API) {
        override fun visit(name: String?, value: Any?) {
            if (value is Type) {
                maybeAddDependentType(types, value)
            }
        }

        override fun visitArray(name: String?): org.objectweb.asm.AnnotationVisitor {
            return this
        }

        override fun visitAnnotation(name: String?, descriptor: String): org.objectweb.asm.AnnotationVisitor {
            maybeAddDependentType(types, Type.getType(descriptor))
            return this
        }
    }

    companion object {
        private val API = AsmConstants.ASM_LEVEL

        /**
         * Handle describing [java.lang.invoke.ConstantBootstraps.invoke].
         */
        private val CONSTANT_BOOTSTRAPS_INVOKE = Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/ConstantBootstraps",
            "invoke",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
            false
        )

        /**
         * Handle describing [java.lang.constant.ClassDesc.of].
         */
        private val CLASS_DESC_OF = Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/constant/ClassDesc",
            "of",
            "(Ljava/lang/String;)Ljava/lang/constant/ClassDesc;",
            true
        )

        fun analyze(className: String?, reader: ClassReader, interner: StringInterner): ClassAnalysis {
            val visitor = ClassDependenciesVisitor(ClassRelevancyFilter(className), reader, interner)
            reader.accept(visitor, ClassReader.SKIP_FRAMES)

            // Remove the "API accessible" types from the "privately used types"
            visitor.privateClassDependencies.removeAll(visitor.accessibleClassDependencies)
            val name = if (visitor.moduleName != null) visitor.moduleName else className
            return ClassAnalysis(
                interner.intern(name!!),
                visitor.privateClassDependencies,
                visitor.accessibleClassDependencies,
                visitor.dependencyToAllReason,
                visitor.constants
            )
        }

        private fun isAccessible(access: Int): Boolean {
            return (access and Opcodes.ACC_PRIVATE) == 0
        }

        private fun isAccessibleConstant(access: Int, value: Any?): Boolean {
            return isConstant(access) && isAccessible(access) && value != null
        }

        private fun isConstant(access: Int): Boolean {
            return (access and Opcodes.ACC_FINAL) != 0 && (access and Opcodes.ACC_STATIC) != 0
        }
    }
}
