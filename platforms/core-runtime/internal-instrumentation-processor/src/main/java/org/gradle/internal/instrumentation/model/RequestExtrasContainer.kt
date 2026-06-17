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
package org.gradle.internal.instrumentation.model

import java.util.Collections
import java.util.Optional

class RequestExtrasContainer {
    private val extras: MutableList<RequestExtra?> = ArrayList<RequestExtra?>()

    val all: MutableList<RequestExtra?>
        get() = Collections.unmodifiableList<RequestExtra?>(extras)

    fun <T : RequestExtra> getByType(type: Class<T>): Optional<T> {
        for (extra in extras) {
            if (extra != null && type.isInstance(extra)) {
                return Optional.of(type.cast(extra))
            }
        }
        return Optional.empty()
    }

    fun add(extra: RequestExtra?) {
        extras.add(extra)
    }
}
