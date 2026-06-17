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
package org.gradle.internal.instrumentation.processor.codegen.groovy

import org.gradle.internal.instrumentation.model.CallInterceptionRequest
import org.gradle.internal.instrumentation.model.CallableInfo
import org.gradle.internal.instrumentation.model.CallableKindInfo
import org.gradle.internal.instrumentation.model.ParameterInfo
import org.gradle.internal.instrumentation.model.ParameterKindInfo
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream

internal class SignatureTree {
    var leafOrNull: CallInterceptionRequest? = null
        private set
    private var childrenByMatchEntry: LinkedHashMap<ParameterMatchEntry?, SignatureTree>? = null

    fun getChildrenByMatchEntry(): MutableMap<ParameterMatchEntry?, SignatureTree?> {
        return if (childrenByMatchEntry != null) childrenByMatchEntry else mutableMapOf<ParameterMatchEntry?, SignatureTree?>()
    }

    fun add(request: CallInterceptionRequest) {
        val callable: CallableInfo = request.interceptedCallable!!
        val matchEntries: MutableList<ParameterMatchEntry> = parameterMatchEntries(callable)

        var current = this
        for (matchEntry in matchEntries) {
            if (current.childrenByMatchEntry == null) {
                current.childrenByMatchEntry = LinkedHashMap<ParameterMatchEntry?, SignatureTree>()
            }
            check(
                !(matchEntry.kind == ParameterMatchEntry.Kind.VARARG && current.childrenByMatchEntry!!.keys.stream()
                    .anyMatch { it: ParameterMatchEntry? -> it!!.kind == ParameterMatchEntry.Kind.VARARG })
            ) { "vararg overloads are not supported yet" }
            current = current.childrenByMatchEntry!!.computeIfAbsent(matchEntry) { key: ParameterMatchEntry? -> SignatureTree() }
        }
        check(current.leafOrNull == null) { "duplicate request" }
        current.leafOrNull = request
    }

    companion object {
        private fun parameterMatchEntries(callable: CallableInfo): MutableList<ParameterMatchEntry> {
            val varargParameter = callable.parameters!!.stream().filter({ it -> it!!.kind === ParameterKindInfo.VARARG_METHOD_PARAMETER }).findAny()
            val kind = callable.kind
            return Stream.of<T?>( // Match the `Class<?>` in `receiver` for static methods and constructors
                if (kind == CallableKindInfo.STATIC_METHOD || kind == CallableKindInfo.AFTER_CONSTRUCTOR)
                    Stream.of<ParameterMatchEntry?>(ParameterMatchEntry(callable.owner!!.type, ParameterMatchEntry.Kind.RECEIVER_AS_CLASS))
                else
                    Stream.empty<ParameterMatchEntry?>(),  // Or match the receiver in the first parameter
                if (kind == CallableKindInfo.INSTANCE_METHOD || kind == CallableKindInfo.GROOVY_PROPERTY_GETTER || kind == CallableKindInfo.GROOVY_PROPERTY_SETTER)
                    Stream.of<ParameterMatchEntry?>(ParameterMatchEntry(callable.parameters!!.get(0)!!.parameterType, ParameterMatchEntry.Kind.RECEIVER))
                else
                    Stream.empty<ParameterMatchEntry?>(),  // Then match the "normal" method parameters
                callable.parameters!!.stream().filter({ it -> it!!.kind === ParameterKindInfo.METHOD_PARAMETER })
                    .map({ it -> ParameterMatchEntry(it!!.parameterType, ParameterMatchEntry.Kind.PARAMETER) }),  // In the end, match the vararg parameter, if it is there:
                varargParameter.map<Stream<ParameterMatchEntry?>?>(Function { parameterInfo: ParameterInfo? ->
                    Stream.of<ParameterMatchEntry?>(
                        ParameterMatchEntry(
                            parameterInfo!!.parameterType.getElementType(),
                            ParameterMatchEntry.Kind.VARARG
                        )
                    )
                }).orElseGet(Supplier { Stream.empty() })
            ).flatMap<R?>(Function.identity<Any?>()).collect(Collectors.toList())
        }
    }
}
