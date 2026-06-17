/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.base.Objects
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

class DefaultTypedAdditionalData(private val type: SerializedPayload, private val byteSerializedIsolate: ByteArray) : TypedAdditionalData {
    override fun getSerializedType(): Any {
        return type
    }

    override fun equals(o: Any): Boolean {
        if (o !is DefaultTypedAdditionalData) {
            return false
        }
        val that = o
        return byteSerializedIsolate.contentEquals(that.byteSerializedIsolate) && Objects.equal(type, that.type)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(byteSerializedIsolate.contentHashCode(), type)
    }

    override fun getBytesForIsolatedObject(): ByteArray {
        return byteSerializedIsolate
    }
}
