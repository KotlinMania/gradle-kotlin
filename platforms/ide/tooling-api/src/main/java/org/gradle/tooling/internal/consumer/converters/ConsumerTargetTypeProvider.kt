/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.tooling.internal.consumer.converters

import org.gradle.tooling.internal.adapter.TargetTypeProvider
import org.gradle.tooling.internal.protocol.cpp.InternalCppApplication
import org.gradle.tooling.internal.protocol.cpp.InternalCppExecutable
import org.gradle.tooling.internal.protocol.cpp.InternalCppLibrary
import org.gradle.tooling.internal.protocol.cpp.InternalCppSharedLibrary
import org.gradle.tooling.internal.protocol.cpp.InternalCppStaticLibrary
import org.gradle.tooling.internal.protocol.cpp.InternalCppTestSuite
import org.gradle.tooling.model.cpp.CppApplication
import org.gradle.tooling.model.cpp.CppBinary
import org.gradle.tooling.model.cpp.CppComponent
import org.gradle.tooling.model.cpp.CppExecutable
import org.gradle.tooling.model.cpp.CppLibrary
import org.gradle.tooling.model.cpp.CppSharedLibrary
import org.gradle.tooling.model.cpp.CppStaticLibrary
import org.gradle.tooling.model.cpp.CppTestSuite
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

class ConsumerTargetTypeProvider : TargetTypeProvider {
    private val configuredTargetTypes: MutableMap<String?, Class<*>?> = HashMap<String?, Class<*>?>()

    init {
        configuredTargetTypes.put(IdeaSingleEntryLibraryDependency::class.java.getCanonicalName(), IdeaSingleEntryLibraryDependency::class.java)
        configuredTargetTypes.put(IdeaModuleDependency::class.java.getCanonicalName(), BackwardsCompatibleIdeaModuleDependency::class.java)
    }

    override fun <T> getTargetType(initialTargetType: Class<T?>, protocolObject: Any): Class<out T> {
        val interfaces = protocolObject.javaClass.getInterfaces()
        for (i in interfaces) {
            if (configuredTargetTypes.containsKey(i.getName())) {
                return configuredTargetTypes.get(i.getName())!!.asSubclass(initialTargetType) as Class<out T>
            }
        }
        if (initialTargetType.isAssignableFrom(CppComponent::class.java)) {
            if (protocolObject is InternalCppApplication) {
                return CppApplication::class.java.asSubclass(initialTargetType) as Class<out T>
            }
            if (protocolObject is InternalCppLibrary) {
                return CppLibrary::class.java.asSubclass(initialTargetType) as Class<out T>
            }
            if (protocolObject is InternalCppTestSuite) {
                return CppTestSuite::class.java.asSubclass(initialTargetType) as Class<out T>
            }
        } else if (initialTargetType.isAssignableFrom(CppBinary::class.java)) {
            if (protocolObject is InternalCppExecutable) {
                return CppExecutable::class.java.asSubclass(initialTargetType) as Class<out T>
            }
            if (protocolObject is InternalCppSharedLibrary) {
                return CppSharedLibrary::class.java.asSubclass(initialTargetType) as Class<out T>
            }
            if (protocolObject is InternalCppStaticLibrary) {
                return CppStaticLibrary::class.java.asSubclass(initialTargetType) as Class<out T>
            }
        }
        return initialTargetType as Class<out T>
    }
}
