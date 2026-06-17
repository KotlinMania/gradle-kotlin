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
package org.gradle.plugin.devel.tasks.internal

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.OffsetInFileLocation
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.internal.DefaultDeprecationData
import org.gradle.api.problems.internal.DefaultFileLocation
import org.gradle.api.problems.internal.DefaultGeneralData
import org.gradle.api.problems.internal.DefaultLineInFileLocation
import org.gradle.api.problems.internal.DefaultOffsetInFileLocation
import org.gradle.api.problems.internal.DefaultPluginIdLocation
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultPropertyTraceData
import org.gradle.api.problems.internal.DefaultTaskLocation
import org.gradle.api.problems.internal.DefaultTypeValidationData
import org.gradle.api.problems.internal.DeprecationData
import org.gradle.api.problems.internal.DocLinkInternal
import org.gradle.api.problems.internal.GeneralData
import org.gradle.api.problems.internal.PropertyTraceData
import org.gradle.api.problems.internal.TypeValidationData
import org.jspecify.annotations.NullMarked
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.Type
import java.util.Arrays
import java.util.Objects

@NullMarked
object ValidationProblemSerialization {
    private val GSON_BUILDER = createGsonBuilder()

    fun deserialize(lines: String): SerializationResult {
        val gson = GSON_BUILDER.create()
        val type = object : TypeToken<MutableList<MutableList<DefaultProblem>>>() {}.getType()
        val lists = gson.fromJson<MutableList<MutableList<DefaultProblem>>>(lines, type)
        return SerializationResult(lists.get(0), lists.get(1))
    }

    fun serialize(warnings: MutableList<Problem>, errors: MutableList<Problem>): String {
        val gson = createGsonBuilder().create()
        return gson.toJson(Arrays.asList<MutableList<Problem>>(warnings, errors))
    }

    private fun createGsonBuilder(): GsonBuilder {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapterFactory(ProblemReportAdapterFactory())
        gsonBuilder.registerTypeAdapter(ProblemId::class.java, ProblemIdInstanceCreator())
        gsonBuilder.registerTypeHierarchyAdapter(DocLink::class.java, DocLinkAdapter())
        gsonBuilder.registerTypeHierarchyAdapter(ProblemLocation::class.java, LocationAdapter())
        gsonBuilder.registerTypeHierarchyAdapter(AdditionalData::class.java, AdditionalDataAdapter())
        gsonBuilder.registerTypeAdapterFactory(ThrowableAdapterFactory())

        return gsonBuilder
    }

    class SerializationResult(val warnings: MutableList<DefaultProblem>, val errors: MutableList<DefaultProblem>)

    /**
     * A type adapter factory for [Throwable] that supports serializing and deserializing [Throwable] instances to JSON using GSON.
     *
     *
     * from [here](https://github.com/eclipse-lsp4j/lsp4j/blob/main/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/json/adapters/ThrowableTypeAdapter.java)
     */
    class ThrowableAdapterFactory : TypeAdapterFactory {
        override fun <T> create(gson: Gson, typeToken: TypeToken<T?>): TypeAdapter<T?>? {
            if (!Throwable::class.java.isAssignableFrom(typeToken.getRawType())) {
                return null
            }

            return ValidationProblemSerialization.ThrowableTypeAdapter(typeToken as TypeToken<Throwable?>) as TypeAdapter<T?>
        }
    }

    /**
     * A type adapter for [Throwable] that supports serializing and deserializing [Throwable] instances to JSON using GSON.
     *
     *
     * from [here](https://github.com/eclipse-lsp4j/lsp4j/blob/main/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/json/adapters/ThrowableTypeAdapter.java)
     */
    class ThrowableTypeAdapter(private val typeToken: TypeToken<Throwable>) : TypeAdapter<Throwable>() {
        @Throws(IOException::class)
        override fun read(`in`: JsonReader): Throwable? {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }

            `in`.beginObject()
            var message: String? = null
            var cause: Throwable? = null
            while (`in`.hasNext()) {
                val name = `in`.nextName()
                when (name) {
                    "message" -> {
                        message = `in`.nextString()
                    }

                    "cause" -> {
                        cause = read(`in`)
                    }

                    else -> `in`.skipValue()
                }
            }
            `in`.endObject()

            try {
                val constructor: Constructor<Throwable>?
                if (message == null && cause == null) {
                    constructor = typeToken.getRawType().getDeclaredConstructor() as Constructor<Throwable>
                    return constructor.newInstance()
                } else if (message == null) {
                    constructor = typeToken.getRawType()
                        .getDeclaredConstructor(Throwable::class.java) as Constructor<Throwable>
                    return constructor.newInstance(cause)
                } else if (cause == null) {
                    constructor = typeToken.getRawType().getDeclaredConstructor(String::class.java) as Constructor<Throwable>
                    return constructor.newInstance(message)
                } else {
                    constructor = typeToken.getRawType().getDeclaredConstructor(
                        String::class.java,
                        Throwable::class.java
                    ) as Constructor<Throwable>
                    return constructor.newInstance(message, cause)
                }
            } catch (e: NoSuchMethodException) {
                if (message == null && cause == null) {
                    return RuntimeException()
                } else if (message == null) {
                    return RuntimeException(cause)
                } else if (cause == null) {
                    return RuntimeException(message)
                } else {
                    return RuntimeException(message, cause)
                }
            } catch (e: Exception) {
                throw JsonParseException(e)
            }
        }

        @Throws(IOException::class)
        override fun write(out: JsonWriter, throwable: Throwable?) {
            if (throwable == null) {
                out.nullValue()
            } else if (throwable.message == null && throwable.cause != null) {
                write(out, throwable.cause)
            } else {
                out.beginObject()
                if (throwable.message != null) {
                    out.name("message")
                    out.value(throwable.message)
                }
                if (shouldWriteCause(throwable)) {
                    out.name("cause")
                    write(out, throwable.cause)
                }
                out.endObject()
            }
        }

        companion object {
            private fun shouldWriteCause(throwable: Throwable): Boolean {
                val cause = throwable.cause
                if (cause == null || cause.message == null || cause === throwable) {
                    return false
                }
                return throwable.message == null || !throwable.message!!.contains(cause.message!!)
            }
        }
    }

    class FileLocationAdapter : TypeAdapter<FileLocation>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: FileLocation?) {
            if (value == null) {
                out.nullValue()
                return
            }

            out.beginObject()
            out.name("type").value("file")
            out.name("path").value(value.path)
            if (value is LineInFileLocation) {
                out.name("subtype").value("lineInFile")
                val l = value
                out.name("line").value(l.line.toLong())
                out.name("column").value(l.column.toLong())
                out.name("length").value(l.length.toLong())
            } else if (value is OffsetInFileLocation) {
                out.name("subtype").value("offsetInFile")
                val l = value
                out.name("offset").value(l.offset.toLong())
                out.name("length").value(l.length.toLong())
            } else {
                out.name("subtype").value("file")
            }
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): FileLocation {
            `in`.beginObject()
            val fileLocation: FileLocation = readObject(`in`)
            `in`.endObject()

            Objects.requireNonNull<FileLocation>(fileLocation, "path must not be null")
            return fileLocation
        }

        companion object {
            @Throws(IOException::class)
            private fun readObject(`in`: JsonReader): FileLocation {
                var subtype: String? = null
                var path: String? = null
                var offset: Int? = null
                var line: Int? = null
                var column: Int? = null
                var length: Int? = null
                while (`in`.hasNext()) {
                    val name = `in`.nextName()
                    when (name) {
                        "subtype" -> {
                            subtype = `in`.nextString()
                        }

                        "path" -> {
                            path = `in`.nextString()
                        }

                        "offset" -> {
                            offset = `in`.nextInt()
                        }

                        "line" -> {
                            line = `in`.nextInt()
                        }

                        "column" -> {
                            column = `in`.nextInt()
                        }

                        "length" -> {
                            length = `in`.nextInt()
                        }

                        else -> `in`.skipValue()
                    }
                }

                if (subtype == "lineInFile") {
                    return DefaultLineInFileLocation.from(path!!, line!!, column!!, length!!)
                } else if (subtype == "offsetInFile") {
                    return DefaultOffsetInFileLocation.from(path!!, offset!!, length!!)
                } else {
                    return DefaultFileLocation.from(path!!)
                }
            }
        }
    }

    class PluginIdLocationAdapter : TypeAdapter<DefaultPluginIdLocation>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: DefaultPluginIdLocation?) {
            if (value == null) {
                out.nullValue()
                return
            }

            out.beginArray()
            out.name("type").value("pluginId")
            out.name("pluginId").value(value.getPluginId())
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): DefaultPluginIdLocation {
            `in`.beginObject()
            val problemLocation: DefaultPluginIdLocation = readObject(`in`)
            `in`.endObject()

            Objects.requireNonNull<DefaultPluginIdLocation>(problemLocation, "pluginId must not be null")
            return problemLocation
        }

        companion object {
            @Throws(IOException::class)
            private fun readObject(`in`: JsonReader): DefaultPluginIdLocation {
                var pluginId: String? = null
                while (`in`.hasNext()) {
                    val name = `in`.nextName()
                    if (name == "pluginId") {
                        pluginId = `in`.nextString()
                    } else {
                        `in`.skipValue()
                    }
                }
                return DefaultPluginIdLocation(pluginId)
            }
        }
    }

    class TaskLocationAdapter : TypeAdapter<DefaultTaskLocation>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: DefaultTaskLocation?) {
            if (value == null) {
                out.nullValue()
                return
            }

            out.beginArray()
            out.name("type").value("task")
            out.name("buildTreePath").value(value.getBuildTreePath())
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): DefaultTaskLocation {
            `in`.beginObject()
            val buildTreePath: DefaultTaskLocation = readObject(`in`)
            `in`.endObject()

            Objects.requireNonNull<DefaultTaskLocation>(buildTreePath, "buildTreePath must not be null")
            return buildTreePath
        }

        companion object {
            @Throws(IOException::class)
            private fun readObject(`in`: JsonReader): DefaultTaskLocation {
                var buildTreePath: String? = null
                while (`in`.hasNext()) {
                    val name = `in`.nextName()
                    if (name == "buildTreePath") {
                        buildTreePath = `in`.nextString()
                    } else {
                        `in`.skipValue()
                    }
                }
                return DefaultTaskLocation(buildTreePath!!)
            }
        }
    }

    class DocLinkAdapter : TypeAdapter<DocLink>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: DocLink?) {
            if (value == null) {
                out.nullValue()
                return
            }

            out.beginObject()
            out.name("url").value(value.url)
            out.name("consultDocumentationMessage").value((value as DocLinkInternal).consultDocumentationMessage)
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): DocLink {
            `in`.beginObject()
            var url: String? = null
            var consultDocumentationMessage: String? = null
            while (`in`.hasNext()) {
                val name = `in`.nextName()
                when (name) {
                    "url" -> {
                        url = `in`.nextString()
                    }

                    "consultDocumentationMessage" -> {
                        consultDocumentationMessage = `in`.nextString()
                    }

                    else -> `in`.skipValue()
                }
            }
            `in`.endObject()

            val finalUrl = url
            val finalConsultDocumentationMessage = consultDocumentationMessage
            return object : DocLinkInternal {
                override fun getUrl(): String {
                    return finalUrl!!
                }

                override fun getConsultDocumentationMessage(): String {
                    return finalConsultDocumentationMessage!!
                }
            }
        }
    }

    private class LocationAdapter : TypeAdapter<ProblemLocation>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: ProblemLocation) {
            if (value == null) {
                out.nullValue()
                return
            }

            if (value is DefaultFileLocation) {
                FileLocationAdapter().write(out, value)
                return
            }
            if (value is DefaultPluginIdLocation) {
                PluginIdLocationAdapter().write(out, value)
                return
            }
            if (value is DefaultTaskLocation) {
                TaskLocationAdapter().write(out, value)
            }
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): ProblemLocation {
            if (`in`.hasNext()) {
                `in`.beginObject()
                try {
                    var type: String? = null
                    val name = `in`.nextName()
                    if (name == "type") {
                        type = `in`.nextString()
                    }
                    if (type == null) {
                        throw JsonParseException("type must not be null")
                    }

                    when (type) {
                        "file" -> return FileLocationAdapter.Companion.readObject(`in`)
                        "pluginId" -> return PluginIdLocationAdapter.Companion.readObject(`in`)
                        "task" -> return TaskLocationAdapter.Companion.readObject(`in`)
                        else -> throw JsonParseException("Unknown type: " + type)
                    }
                } finally {
                    `in`.endObject()
                }
            }
            return null
        }
    }

    private class ProblemIdInstanceCreator : JsonDeserializer<ProblemId>, JsonSerializer<ProblemId> {
        @Throws(JsonParseException::class)
        override fun deserialize(jsonElement: JsonElement, type: Type, jsonDeserializationContext: JsonDeserializationContext): ProblemId {
            val problemObject = jsonElement.getAsJsonObject()
            val name = problemObject.get("name").getAsString()
            val displayName = problemObject.get("displayName").getAsString()
            val group: ProblemGroup = deserializeGroup(problemObject.get("group"))
            return ProblemId.create(name, displayName, group)
        }

        override fun serialize(problemId: ProblemId, type: Type, jsonSerializationContext: JsonSerializationContext): JsonElement {
            val result = JsonObject()
            result.addProperty("name", problemId.name)
            result.addProperty("displayName", problemId.displayName)
            result.add("group", serializeGroup(problemId.group))
            return result
        }


        companion object {
            private fun deserializeGroup(groupObject: JsonElement): ProblemGroup {
                val group = groupObject.getAsJsonObject()
                val name = group.get("name").getAsString()
                val displayName = group.get("displayName").getAsString()
                val parent = group.get("parent")
                if (parent == null) {
                    return ProblemGroup.create(name, displayName)
                }
                return ProblemGroup.create(name, displayName, deserializeGroup(parent))
            }

            private fun serializeGroup(group: ProblemGroup): JsonObject {
                val groupObject = JsonObject()
                groupObject.addProperty("name", group.name)
                groupObject.addProperty("displayName", group.displayName)
                val parent = group.parent
                if (parent != null) {
                    groupObject.add("parent", serializeGroup(parent))
                }
                return groupObject
            }
        }
    }

    private class AdditionalDataAdapter : TypeAdapter<AdditionalData>() {
        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: AdditionalData) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.beginObject()
            if (value is DeprecationData) {
                out.name(ADDITIONAL_DATA_TYPE).value(DEPRECATION_DATA)
                out.name(FEATURE_USAGE).value(value.type.name)
            } else if (value is TypeValidationData) {
                out.name(ADDITIONAL_DATA_TYPE).value(TYPE_VALIDATION_DATA)
                val typeValidationData = value
                out.name(PLUGIN_ID).value(typeValidationData.pluginId)
                out.name(PROPERTY_NAME).value(typeValidationData.propertyName)
                out.name(FUNCTION_NAME).value(typeValidationData.functionName)
                out.name(PARENT_PROPERTY_NAME).value(typeValidationData.parentPropertyName)
                out.name(TYPE_NAME).value(typeValidationData.typeName)
            } else if (value is GeneralData) {
                out.name(ADDITIONAL_DATA_TYPE).value(GENERAL_DATA)
                out.name(GENERAL_DATA_DATA)
                out.beginObject()
                val map = value.asMap
                for (key in map.keys) {
                    out.name(key).value(map.get(key))
                }
                out.endObject()
            } else if (value is PropertyTraceData) {
                out.name(ADDITIONAL_DATA_TYPE).value(PROPERTY_TRACE_DATA)
                out.name(PROPERTY_TRACE).value(value.trace)
            }
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): AdditionalData {
            if (!`in`.hasNext()) {
                return null
            }
            `in`.beginObject()
            try {
                var type: String? = null
                var featureUsage: String? = null
                var pluginId: String? = null
                var propertyName: String? = null
                var functionName: String? = null
                var parentPropertyName: String? = null
                var typeName: String? = null
                var name: String
                var generalData: MutableMap<String, String>? = null
                var propertyTrace: String? = null

                while (`in`.hasNext()) {
                    name = `in`.nextName()
                    when (name) {
                        ADDITIONAL_DATA_TYPE -> {
                            type = `in`.nextString()
                        }

                        FEATURE_USAGE -> {
                            featureUsage = `in`.nextString()
                        }

                        PLUGIN_ID -> {
                            pluginId = `in`.nextString()
                        }

                        PROPERTY_NAME -> {
                            propertyName = `in`.nextString()
                        }

                        FUNCTION_NAME -> {
                            functionName = `in`.nextString()
                        }

                        PARENT_PROPERTY_NAME -> {
                            parentPropertyName = `in`.nextString()
                        }

                        TYPE_NAME -> {
                            typeName = `in`.nextString()
                        }

                        PROPERTY_TRACE -> {
                            propertyTrace = `in`.nextString()
                        }

                        GENERAL_DATA_DATA -> {
                            try {
                                `in`.beginObject()
                                generalData = HashMap<String, String>()
                                while (`in`.hasNext()) {
                                    val key = `in`.nextName()
                                    val value = `in`.nextString()
                                    generalData.put(key, value!!)
                                }
                            } finally {
                                `in`.endObject()
                            }
                        }

                        else -> `in`.skipValue()
                    }
                }
                if (type == null) {
                    throw JsonParseException("type must not be null")
                }
                return Companion.createAdditionalData(type, featureUsage!!, pluginId!!, propertyName!!, functionName!!, parentPropertyName!!, typeName!!, generalData!!, propertyTrace!!)
            } finally {
                `in`.endObject()
            }
        }

        companion object {
            const val PROPERTY_TRACE_DATA: String = "propertyTraceData"
            const val PROPERTY_TRACE: String = "propertyTrace"
            const val ADDITIONAL_DATA_TYPE: String = "type"
            const val DEPRECATION_DATA: String = "deprecationData"
            const val TYPE_VALIDATION_DATA: String = "typeValidationData"
            const val GENERAL_DATA: String = "generalData"
            const val FEATURE_USAGE: String = "featureUsage"
            const val PLUGIN_ID: String = "pluginId"
            const val PROPERTY_NAME: String = "propertyName"
            const val FUNCTION_NAME: String = "functionName"
            const val PARENT_PROPERTY_NAME: String = "parentPropertyName"
            const val TYPE_NAME: String = "typeName"
            const val GENERAL_DATA_DATA: String = "data"

            private fun createAdditionalData(
                type: String,
                featureUsage: String,
                pluginId: String,
                propertyName: String,
                methodName: String,
                parentPropertyName: String,
                typeName: String,
                generalData: MutableMap<String, String>,
                propertyTrace: String
            ): AdditionalData {
                when (type) {
                    DEPRECATION_DATA -> return DefaultDeprecationData(DeprecationData.Type.valueOf(featureUsage))
                    TYPE_VALIDATION_DATA -> return DefaultTypeValidationData(
                        pluginId,
                        propertyName,
                        methodName,
                        parentPropertyName,
                        typeName
                    )

                    GENERAL_DATA -> return DefaultGeneralData(generalData)
                    PROPERTY_TRACE_DATA -> return DefaultPropertyTraceData(propertyTrace)
                    else -> throw JsonParseException("Unknown type: " + type)
                }
            }
        }
    }
}
