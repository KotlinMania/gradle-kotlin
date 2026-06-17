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
package org.gradle.plugin.devel.tasks.internal

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.internal.DefaultProblemDefinition
import java.io.IOException

/**
 * Defines the Gson serialization and deserialization for [ProblemDefinition] based on the assumption that they have exactly one implementation.
 */
class ProblemReportAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T?>?): TypeAdapter<T?>? {
        if (type == null) {
            return null
        }
        val rawType: Class<*>? = type.getRawType()
        if (ProblemDefinition::class.java == rawType) {
            return ProblemReportAdapterFactory.SingleImplTypeAdapter<ProblemDefinition, DefaultProblemDefinition>(
                ProblemDefinition::class.java,
                DefaultProblemDefinition::class.java,
                gson.getAdapter<JsonElement>(JsonElement::class.java),
                gson.getDelegateAdapter<DefaultProblemDefinition>(this, TypeToken.get<DefaultProblemDefinition>(DefaultProblemDefinition::class.java))
            ).nullSafe() as TypeAdapter<T?>
        } else {
            return null
        }
    }

    private class SingleImplTypeAdapter<T, U>(
        private val baseClass: Class<T?>,
        private val implClass: Class<U?>,
        private val jsonElementAdapter: TypeAdapter<JsonElement>,
        private val implClassAdapter: TypeAdapter<U?>
    ) : TypeAdapter<T?>() {
        private val label: String

        init {
            this.label = baseClass.getSimpleName()
            if (!baseClass.isAssignableFrom(implClass)) {
                throw JsonParseException(implClass.toString() + " is not a subclass of " + baseClass)
            }
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): T? {
            val jsonElement = jsonElementAdapter.read(`in`)
            return implClassAdapter.fromJsonTree(jsonElement) as T
        }

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: T?) {
            if (!implClass.isInstance(value)) {
                throw JsonParseException("Unknown concrete type for " + baseClass + ". Expected: " + implClass + ", actual: " + value!!.javaClass)
            }
            val jsonObject = implClassAdapter.toJsonTree(value as U).getAsJsonObject()
            val clone = JsonObject()
            clone.add(label, JsonPrimitive(label))
            for (e in jsonObject.entrySet()) {
                clone.add(e.key, e.value)
            }
            jsonElementAdapter.write(out, clone)
        }
    }
}
