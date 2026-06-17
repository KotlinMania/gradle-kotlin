/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.instrumentation.api.jvmbytecode

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter
import org.gradle.model.internal.asm.MethodVisitorScope
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.Arrays
import javax.annotation.CheckReturnValue

/**
 * The implementation of the bridge method builder that handles typical invocation cases and can compute the bridge method signature.
 */
abstract class DefaultBridgeMethodBuilder private constructor(
    private val bridgeDesc: String,
    private val interceptorOwner: String,
    private val interceptorName: String,
    private val interceptorDesc: String,
    private val hasKotlinDefaultMask: Boolean = false,
    private val binaryClassName: String? = null,
    private val context: BytecodeInterceptorFilter? = null
) : BridgeMethodBuilder {
    /**
     * Creates the copy of the provided bridge method builder with an adjusted bridge method descriptor.
     * The bridge descriptor isn't validated for compatibility with the provided builder.
     *
     * @param builder the builder to copy the other data from
     * @param bridgeDesc the new bridge method descriptor
     */
    protected constructor(builder: DefaultBridgeMethodBuilder, bridgeDesc: String) : this(
        bridgeDesc,
        builder.interceptorOwner,
        builder.interceptorName,
        builder.interceptorDesc,
        builder.hasKotlinDefaultMask,
        builder.binaryClassName,
        builder.context
    )

    /**
     * Creates the copy of the provided bridge method builder with adjusted extra parameters.
     *
     * @param builder the builder to copy the other data from
     * @param hasKotlinDefaultMask if the interceptor method accepts Kotlin default mask argument
     * @param binaryClassName if the interceptor method accepts a binary class name of the class where rewrite happens
     * @param context if the interceptor method accepts the intercepting context
     *
     * @see .copy
     */
    protected constructor(
        builder: DefaultBridgeMethodBuilder,
        hasKotlinDefaultMask: Boolean,
        binaryClassName: String?,
        context: BytecodeInterceptorFilter?
    ) : this(builder.bridgeDesc, builder.interceptorOwner, builder.interceptorName, builder.interceptorDesc, hasKotlinDefaultMask, binaryClassName, context)

    override fun withReceiverType(targetType: String): BridgeMethodBuilder? {
        throw UnsupportedOperationException("Receiver type refinement isn't supported for " + javaClass.getSimpleName())
    }

    /**
     * Use when the interceptor method handles Kotlin method with default parameter values.
     *
     * @return adjusted builder
     */
    @CheckReturnValue
    fun withKotlinDefaultMask(): DefaultBridgeMethodBuilder {
        return copy(true, binaryClassName, context)
    }

    /**
     * Pass the provided class name to the interceptor method after the original arguments.
     *
     * @param className the class name
     * @return adjusted builder
     */
    @CheckReturnValue
    fun withClassName(className: String): DefaultBridgeMethodBuilder {
        return copy(hasKotlinDefaultMask, Type.getObjectType(className).getClassName(), context)
    }

    /**
     * Pass the provided filter to the interceptor method after the original arguments.
     *
     * @param context the context
     * @return adjusted builder
     */
    @CheckReturnValue
    fun withVisitorContext(context: BytecodeInterceptorFilter): DefaultBridgeMethodBuilder {
        return copy(hasKotlinDefaultMask, binaryClassName, context)
    }

    protected abstract fun copy(hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?): DefaultBridgeMethodBuilder

    override val bridgeMethodDescriptor: String
        get() = bridgeDesc

    override fun buildBridgeMethod(methodVisitor: MethodVisitor) {
        val mv = MethodVisitorScope(methodVisitor)
        buildBridgeMethodImpl(mv)
        // We rely on COMPUTE_MAXs.
        // TODO(mlopatkin) Can we get a proper value?
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }

    /**
     * Generates the basic interceptor implementation that copies bridge method arguments onto stack, adds contextual arguments like caller class name, and invokes the interceptor method.
     * Whatever left on stack is returned.
     *
     * @param mv the method visitor to write bytecode with
     */
    protected open fun buildBridgeMethodImpl(mv: MethodVisitorScope) {
        copyBridgeMethodArgsOnStack(mv)

        if (hasKotlinDefaultMask) {
            // Note that we cannot reasonably get a method reference to a Kotlin method that accepts the default mask.
            // A proxy method generated by the Kotlin compiler calls this method normally, and we instrument that method call.
            // However, in theory, we can see a reference to the method that accepts all arguments, though currently the compiler generates a proxy method too.
            // Either way, we just signal the interceptor that all arguments are provided.
            mv._LDC(0)
        }
        if (binaryClassName != null) {
            mv._LDC(binaryClassName)
        }
        if (context != null) {
            mv._GETSTATIC(VISITOR_CONTEXT_TYPE, context.name, VISITOR_CONTEXT_TYPE.getDescriptor())
        }

        mv._INVOKESTATIC(interceptorOwner, interceptorName, interceptorDesc)
        mv._IRETURN_OF(this.bridgeMethod.getReturnType())
    }

    /**
     * Helper to copy all bridge method arguments onto stack
     *
     * @param mv the method visitor to write bytecode with
     */
    protected fun copyBridgeMethodArgsOnStack(mv: MethodVisitorScope) {
        val args = this.bridgeMethod.getArgumentTypes()
        for (i in args.indices) {
            mv._ILOAD_OF(args[i], i)
        }
    }

    private val bridgeMethod: Type
        get() = Type.getMethodType(bridgeDesc)

    private class StaticBridgeMethodBuilder : DefaultBridgeMethodBuilder {
        internal constructor(bridgeDesc: String, interceptorOwner: String, interceptorName: String, interceptorDesc: String) : super(bridgeDesc, interceptorOwner, interceptorName, interceptorDesc)

        internal constructor(builder: StaticBridgeMethodBuilder, hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?) : super(
            builder,
            hasKotlinDefaultMask,
            binaryClassName,
            context
        )

        override fun copy(hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?): StaticBridgeMethodBuilder {
            return StaticBridgeMethodBuilder(this, hasKotlinDefaultMask, binaryClassName, context)
        }
    }

    private class ConstructorBridgeMethodBuilder : DefaultBridgeMethodBuilder {
        private val originalOwner: String
        private val originalConstructorDesc: String

        internal constructor(
            originalOwner: String,
            originalConstructorDesc: String,
            interceptorOwner: String,
            interceptorName: String,
            interceptorDesc: String
        ) : super(buildConstructorBridgeDesc(originalOwner, originalConstructorDesc), interceptorOwner, interceptorName, interceptorDesc) {
            this.originalOwner = originalOwner
            this.originalConstructorDesc = originalConstructorDesc
        }

        internal constructor(builder: ConstructorBridgeMethodBuilder, hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?) : super(
            builder,
            hasKotlinDefaultMask,
            binaryClassName,
            context
        ) {
            this.originalOwner = builder.originalOwner
            this.originalConstructorDesc = builder.originalConstructorDesc
        }

        override fun copy(hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?): ConstructorBridgeMethodBuilder {
            return ConstructorBridgeMethodBuilder(this, hasKotlinDefaultMask, binaryClassName, context)
        }

        override fun buildBridgeMethodImpl(mv: MethodVisitorScope) {
            // Unlike the INVOKESPECIAL opcode, a method reference with NEWINVOKESPECIAL tag cannot represent super constructor call.
            // Thus, we can simply call the constructor ourselves.
            val ownerType = Type.getObjectType(originalOwner)
            mv._NEW(ownerType)
            mv._DUP()
            copyBridgeMethodArgsOnStack(mv)
            mv._INVOKESPECIAL(ownerType, "<init>", originalConstructorDesc) // <ref>, {<ref>, arg0, ..., argN} -> {}
            mv._DUP()
            // <ref>, <ref>
            // The top <ref> will be consumed by the interceptor.
            // The constructor's interceptor returns nothing, so the next <ref> will be used as the return value.
            super.buildBridgeMethodImpl(mv)
        }

        companion object {
            private fun buildConstructorBridgeDesc(owner: String, desc: String): String {
                // The constructor is represented as NEWINVOKESPECIAL:Owner.<init>(...)V. The bridge method have to call
                // the constructor itself and return the constructed value. Thus, we change the return value to Owner.
                val originalOwner = Type.getObjectType(owner)
                val originalMethodType = Type.getMethodType(desc)
                return Type.getMethodDescriptor(originalOwner, *originalMethodType.getArgumentTypes())
            }
        }
    }

    private class InstanceBridgeMethodBuilder : DefaultBridgeMethodBuilder {
        private val tag: Int
        private val originalDesc: String

        internal constructor(tag: Int, originalOwner: String, originalDesc: String, interceptorOwner: String, interceptorName: String, interceptorDesc: String) : super(
            buildInstanceBridgeDesc(
                tag,
                originalOwner,
                originalDesc
            ), interceptorOwner, interceptorName, interceptorDesc
        ) {
            this.tag = tag
            this.originalDesc = originalDesc
        }

        internal constructor(refinedOwner: String, builder: InstanceBridgeMethodBuilder) : super(builder, buildInstanceBridgeDesc(builder.tag, refinedOwner, builder.originalDesc)) {
            this.tag = builder.tag
            this.originalDesc = builder.originalDesc
        }

        internal constructor(builder: InstanceBridgeMethodBuilder, hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?) : super(
            builder,
            hasKotlinDefaultMask,
            binaryClassName,
            context
        ) {
            this.tag = builder.tag
            this.originalDesc = builder.originalDesc
        }

        override fun copy(hasKotlinDefaultMask: Boolean, binaryClassName: String?, context: BytecodeInterceptorFilter?): InstanceBridgeMethodBuilder {
            return InstanceBridgeMethodBuilder(this, hasKotlinDefaultMask, binaryClassName, context)
        }

        override fun withReceiverType(targetType: String): BridgeMethodBuilder {
            return InstanceBridgeMethodBuilder(targetType, this)
        }

        companion object {
            private fun buildInstanceBridgeDesc(tag: Int, owner: String, desc: String): String {
                assert(tag == Opcodes.H_INVOKEINTERFACE || tag == Opcodes.H_INVOKEVIRTUAL)
                // When intercepting an instance method, the interceptor gets the receiver as the first argument.
                val originalOwner = Type.getObjectType(owner)
                val originalMethodType = Type.getMethodType(desc)

                val interceptorArguments: MutableList<Type> = ArrayList<Type>(originalMethodType.getArgumentCount() + 1)
                interceptorArguments.add(originalOwner)
                interceptorArguments.addAll(Arrays.asList<Type>(*originalMethodType.getArgumentTypes()))

                return Type.getMethodDescriptor(originalMethodType.getReturnType(), *interceptorArguments.toTypedArray<Type>())
            }
        }
    }

    companion object {
        private val VISITOR_CONTEXT_TYPE: Type = Type.getType(BytecodeInterceptorFilter::class.java)

        /**
         * Constructor that accepts the original handle and the interceptor method.
         * The interceptor must be a static method with a specific signature.
         *
         *
         * If the original method is an instance method, then the interceptor method will get the receiver of the original as a first argument.
         * Other arguments of the original method will be passed to the interceptor in the same order.
         *
         *
         * If the original method is a static method, then the interceptor method will get all its arguments in the same order.
         *
         * @param originalTag the tag from the original method handle
         * @param originalOwner the owner of the original method handle
         * @param originalDesc the descriptor of the original method handle
         * @param interceptorOwner the owner of the interceptor method
         * @param interceptorName the name of the interceptor method
         * @param interceptorDesc the descriptor of the interceptor method
         * @see .withClassName
         */
        @JvmStatic
        fun create(
            originalTag: Int,
            originalOwner: String,
            originalDesc: String,
            interceptorOwner: String,
            interceptorName: String,
            interceptorDesc: String
        ): DefaultBridgeMethodBuilder {
            when (originalTag) {
                Opcodes.H_INVOKESTATIC -> {
                    return DefaultBridgeMethodBuilder.StaticBridgeMethodBuilder(originalDesc, interceptorOwner, interceptorName, interceptorDesc)
                }

                Opcodes.H_NEWINVOKESPECIAL -> {
                    require(Type.getMethodType(interceptorDesc).getReturnType().getSort() == Type.VOID) {
                        String.format(
                            "Cannot intercept constructor %s of %s with a non-void returning method %s.%s(%s)!",
                            originalDesc, originalOwner, interceptorOwner, interceptorName, interceptorDesc
                        )
                    }
                    return ConstructorBridgeMethodBuilder(originalOwner, originalDesc, interceptorOwner, interceptorName, interceptorDesc)
                }

                Opcodes.H_INVOKEINTERFACE, Opcodes.H_INVOKEVIRTUAL -> {
                    return InstanceBridgeMethodBuilder(originalTag, originalOwner, originalDesc, interceptorOwner, interceptorName, interceptorDesc)
                }

                else -> throw IllegalArgumentException("Unsupported tag " + originalTag)
            }
        }
    }
}
