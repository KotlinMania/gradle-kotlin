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

import org.objectweb.asm.Type
import java.util.Objects

internal class ParameterMatchEntry(val type: Type?, val kind: Kind?) {
    internal enum class Kind {
        RECEIVER_AS_CLASS, RECEIVER, PARAMETER, VARARG
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is ParameterMatchEntry) {
            return false
        }
        val that = o
        return type == that.type && kind == that.kind
    }

    override fun hashCode(): Int {
        return Objects.hash(type, kind)
    }
}
