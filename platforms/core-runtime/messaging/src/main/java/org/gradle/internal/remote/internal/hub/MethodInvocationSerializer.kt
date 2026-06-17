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
package org.gradle.internal.remote.internal.hub

import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ObjectReader
import org.gradle.internal.serialize.ObjectWriter
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.StatefulSerializer
import java.io.IOException
import java.lang.reflect.Method

class MethodInvocationSerializer(private val classLoader: ClassLoader?, private val methodArgsSerializer: MethodArgsSerializer) : StatefulSerializer<MethodInvocation?> {
    override fun newReader(decoder: Decoder): ObjectReader<MethodInvocation?> {
        return MethodInvocationReader(decoder, classLoader, methodArgsSerializer)
    }

    override fun newWriter(encoder: Encoder): ObjectWriter<MethodInvocation?> {
        return MethodInvocationWriter(encoder, methodArgsSerializer)
    }

    private class MethodDetails(val methodId: Int, val method: Method, val argsSerializer: Serializer<Array<Any?>?>)

    private class MethodInvocationWriter(private val encoder: Encoder, private val methodArgsSerializer: MethodArgsSerializer) : ObjectWriter<MethodInvocation?> {
        private val methods: MutableMap<Method?, MethodDetails?> = HashMap<Method?, MethodDetails?>()

        @Throws(Exception::class)
        override fun write(value: MethodInvocation) {
            require(value.arguments.size == value.method.getParameterTypes().size) { String.format("Mismatched number of parameters to method %s.", value.method) }
            val methodDetails = writeMethod(value.method)
            writeArgs(methodDetails, value)
        }

        @Throws(Exception::class)
        fun writeArgs(methodDetails: MethodDetails, value: MethodInvocation) {
            methodDetails.argsSerializer.write(encoder, value.arguments)
        }

        @Throws(IOException::class)
        fun writeMethod(method: Method): MethodDetails {
            var methodDetails = methods.get(method)
            if (methodDetails == null) {
                val methodId = methods.size
                methodDetails = MethodDetails(methodId, method, methodArgsSerializer.forTypes(method.getParameterTypes()))
                methods.put(method, methodDetails)
                encoder.writeSmallInt(methodId)
                encoder.writeString(method.getDeclaringClass().getName())
                encoder.writeString(method.getName())
                encoder.writeSmallInt(method.getParameterTypes().size)
                for (i in method.getParameterTypes().indices) {
                    val paramType = method.getParameterTypes()[i]
                    encoder.writeString(paramType.getName())
                }
            } else {
                encoder.writeSmallInt(methodDetails.methodId)
            }
            return methodDetails
        }
    }

    private class MethodInvocationReader(private val decoder: Decoder, private val classLoader: ClassLoader?, private val methodArgsSerializer: MethodArgsSerializer) :
        ObjectReader<MethodInvocation?> {
        private val methods: MutableMap<Int?, MethodDetails?> = HashMap<Int?, MethodDetails?>()

        @Throws(Exception::class)
        override fun read(): MethodInvocation {
            val methodDetails = readMethod()
            val args = readArguments(methodDetails)
            return MethodInvocation(methodDetails.method, args)
        }

        @Throws(Exception::class)
        fun readArguments(methodDetails: MethodDetails): Array<Any?>? {
            return methodDetails.argsSerializer.read(decoder)
        }

        @Throws(ClassNotFoundException::class, NoSuchMethodException::class, IOException::class)
        fun readMethod(): MethodDetails {
            val methodId = decoder.readSmallInt()
            var methodDetails = methods.get(methodId)
            if (methodDetails == null) {
                val declaringClass = readType()
                val methodName = decoder.readString()
                val paramCount = decoder.readSmallInt()
                val paramTypes = arrayOfNulls<Class<*>>(paramCount)
                for (i in paramTypes.indices) {
                    paramTypes[i] = readType()
                }
                val method = declaringClass.getDeclaredMethod(methodName, *paramTypes)
                methodDetails = MethodDetails(methodId, method, methodArgsSerializer.forTypes(method.getParameterTypes()))
                methods.put(methodId, methodDetails)
            }
            return methodDetails
        }

        @Throws(ClassNotFoundException::class, IOException::class)
        fun readType(): Class<*> {
            val typeName = decoder.readString()
            var paramType: Class<*>? = PRIMITIVE_TYPES.get(typeName)
            if (paramType == null) {
                paramType = Class.forName(typeName, false, classLoader)
            }
            return paramType
        }

        companion object {
            private val PRIMITIVE_TYPES: MutableMap<String?, Class<*>?>

            init {
                PRIMITIVE_TYPES = HashMap<String?, Class<*>?>()
                PRIMITIVE_TYPES.put(Integer.TYPE.getName(), Integer.TYPE)
            }
        }
    }
}
