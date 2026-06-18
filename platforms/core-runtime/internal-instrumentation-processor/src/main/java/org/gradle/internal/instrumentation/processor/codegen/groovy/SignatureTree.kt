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

internal class SignatureTree {
    var leafOrNull: CallInterceptionRequest? = null
        private set
    private var childrenByMatchEntry: LinkedHashMap<ParameterMatchEntry, SignatureTree>? = null

    fun getChildrenByMatchEntry(): Map<ParameterMatchEntry, SignatureTree> {
        return childrenByMatchEntry ?: emptyMap()
    }

    fun add(request: CallInterceptionRequest) {
        val callable: CallableInfo = requireNotNull(request.interceptedCallable)
        val matchEntries: List<ParameterMatchEntry> = parameterMatchEntries(callable)

        var current = this
        for (matchEntry in matchEntries) {
            if (current.childrenByMatchEntry == null) {
                current.childrenByMatchEntry = LinkedHashMap()
            }
            check(
                !(matchEntry.kind == ParameterMatchEntry.Kind.VARARG && current.childrenByMatchEntry!!.keys
                    .any { it.kind == ParameterMatchEntry.Kind.VARARG })
            ) { "vararg overloads are not supported yet" }
            current = current.childrenByMatchEntry!!.computeIfAbsent(matchEntry) { SignatureTree() }
        }
        check(current.leafOrNull == null) { "duplicate request" }
        current.leafOrNull = request
    }

    companion object {
        private fun parameterMatchEntries(callable: CallableInfo): List<ParameterMatchEntry> {
            val parameters = requireNotNull(callable.parameters)
            val kind = callable.kind
            val entries = ArrayList<ParameterMatchEntry>()

            if (kind == CallableKindInfo.STATIC_METHOD || kind == CallableKindInfo.AFTER_CONSTRUCTOR) {
                entries.add(ParameterMatchEntry(requireNotNull(callable.owner).type, ParameterMatchEntry.Kind.RECEIVER_AS_CLASS))
            }
            if (kind == CallableKindInfo.INSTANCE_METHOD || kind == CallableKindInfo.GROOVY_PROPERTY_GETTER || kind == CallableKindInfo.GROOVY_PROPERTY_SETTER) {
                entries.add(ParameterMatchEntry(parameters[0].parameterType, ParameterMatchEntry.Kind.RECEIVER))
            }
            parameters
                .filter { it.kind == ParameterKindInfo.METHOD_PARAMETER }
                .mapTo(entries) { ParameterMatchEntry(it.parameterType, ParameterMatchEntry.Kind.PARAMETER) }

            parameters
                .firstOrNull { it.kind == ParameterKindInfo.VARARG_METHOD_PARAMETER }
                ?.let { parameterInfo: ParameterInfo ->
                    entries.add(ParameterMatchEntry(requireNotNull(parameterInfo.parameterType).elementType, ParameterMatchEntry.Kind.VARARG))
                }

            return entries
        }
    }
}
