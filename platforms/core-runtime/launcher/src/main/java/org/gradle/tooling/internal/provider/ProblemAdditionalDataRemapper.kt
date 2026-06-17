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
package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.types.DefaultInternalPayloadSerializedAdditionalData
import org.gradle.internal.build.event.types.DefaultInternalProxiedAdditionalData
import org.gradle.internal.build.event.types.DefaultProblemDetails
import org.gradle.internal.build.event.types.DefaultProblemEvent
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classloader.ClassLoaderVisitor
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import java.net.URL
import java.util.Collections

class ProblemAdditionalDataRemapper(
    private val payloadSerializer: PayloadSerializer,
    private val delegate: BuildEventConsumer,
    private val isolatableSerializerRegistry: IsolatableSerializerRegistry
) : BuildEventConsumer {
    override fun dispatch(message: Any) {
        remapAdditionalData(message)
        delegate.dispatch(message)
    }

    private fun remapAdditionalData(message: Any) {
        if (message !is DefaultProblemEvent) {
            return
        }
        val problemEvent = message
        val details = problemEvent.getDetails()
        if (details !is DefaultProblemDetails) {
            return
        }
        val additionalData = details.getAdditionalData()
        if (additionalData !is DefaultInternalPayloadSerializedAdditionalData) {
            return
        }
        val serializedAdditionalData = additionalData
        val serializedType = serializedAdditionalData.getSerializedType() as SerializedPayload

        val type = payloadSerializer.deserialize(serializedType) as Class<*>?
        if (type == null) {
            return
        }

        val isolatableBytes = serializedAdditionalData.getBytesForIsolatadObject()

        val classPath: MutableList<URL> = getClassPath(type)

        val visitableURLClassLoader = VisitableURLClassLoader("name", javaClass.getClassLoader(), classPath)
        val o: Any? = ClassLoaderUtils.executeInClassloader(visitableURLClassLoader, {
            val isolatable = isolatableSerializerRegistry.deserialize(isolatableBytes)
            isolatable.isolate()
        })
        details.setAdditionalData(DefaultInternalProxiedAdditionalData(o!!, serializedType))
    }

    companion object {
        private fun getClassPath(type: Class<*>): MutableList<URL> {
            val classPath: MutableList<URL> = ArrayList<URL>()
            (type.getClassLoader() as VisitableURLClassLoader).visit(object : ClassLoaderVisitor() {
                override fun visitClassPath(urls: Array<URL>) {
                    Collections.addAll<URL>(classPath, *urls)
                }
            })
            return classPath
        }
    }
}
