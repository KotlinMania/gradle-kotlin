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
package org.gradle.internal.jvm.inspection

import com.google.common.collect.Sets
import org.jspecify.annotations.NullMarked

/**
 * Represents something needed in a Java installation.
 */
@NullMarked
enum class JavaInstallationCapability {
    /**
     * The installation has a Java compiler. This is not present for JREs.
     */
    JAVA_COMPILER,

    /**
     * The installation has the Javadoc tool. This is not present for JREs.
     */
    JAVADOC_TOOL,

    /**
     * The installation has the Jar tool. This is not present for JREs.
     */
    JAR_TOOL,

    /**
     * The installation uses the J9 virtual machine. This is only present for IBM J9 JVMs.
     */
    J9_VIRTUAL_MACHINE,

    /**
     * The installation provides the `native-image` binary. This is present for Graal VM compatible installations.
     */
    NATIVE_IMAGE;

    fun toDisplayName(): String {
        when (this) {
            JavaInstallationCapability.JAVA_COMPILER -> return "executable 'javac'"
            JavaInstallationCapability.JAVADOC_TOOL -> return "executable 'javadoc'"
            JavaInstallationCapability.JAR_TOOL -> return "executable 'jar'"
            JavaInstallationCapability.J9_VIRTUAL_MACHINE -> return "J9 virtual machine"
            JavaInstallationCapability.NATIVE_IMAGE -> return "executable 'native-image'"
            else -> throw IllegalStateException("Unknown capability: " + this)
        }
    }

    companion object {
        /**
         * All capabilities needed by our uses of a JDK. When something "is JDK", it has all of these.
         */
        @JvmField
        val JDK_CAPABILITIES: MutableSet<JavaInstallationCapability> =
            Sets.immutableEnumSet<JavaInstallationCapability>(JavaInstallationCapability.JAVA_COMPILER, JavaInstallationCapability.JAVADOC_TOOL, JavaInstallationCapability.JAR_TOOL)
    }
}
