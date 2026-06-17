/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.jacoco

import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.specs.Spec
import org.gradle.util.internal.VersionNumber
import java.io.File
import javax.inject.Inject

/**
 * Helper to resolve the `jacocoagent.jar` from inside of the `org.jacoco.agent.jar`.
 */
class JacocoAgentJar
/**
 * Constructs a new agent JAR wrapper.
 */ @Inject constructor(private val fileOperations: FileOperations) {
    /**
     * @return the configuration that the agent JAR is located in
     */
    var agentConf: FileCollection? = null
    private var agentJar: File? = null

    val jar: File
        /**
         * Unzips the resolved `org.jacoco.agent.jar` to retrieve the `jacocoagent.jar`.
         *
         * @return a file pointing to the `jacocoagent.jar`
         */
        get() {
            if (agentJar == null) {
                agentJar = fileOperations.zipTree(this.agentConf!!.getSingleFile()).filter(object : Spec<File?> {
                    override fun isSatisfiedBy(file: File): Boolean {
                        return file.getName() == "jacocoagent.jar"
                    }
                }).getSingleFile()
            }
            return agentJar
        }

    fun supportsJmx(): Boolean {
        val pre062 = Iterables.any<File>(this.agentConf!!, object : Predicate<File> {
            override fun apply(file: File): Boolean {
                return V_0_6_2_0.compareTo(extractVersion(file.getName())) > 0
            }
        })
        return !pre062
    }

    fun supportsInclNoLocationClasses(): Boolean {
        val pre076 = Iterables.any<File>(this.agentConf!!, object : Predicate<File> {
            override fun apply(file: File): Boolean {
                return V_0_7_6_0.compareTo(extractVersion(file.getName())) > 0
            }
        })
        return !pre076
    }

    companion object {
        private val V_0_6_2_0: VersionNumber = VersionNumber.parse("0.6.2.0")
        private val V_0_7_6_0: VersionNumber = VersionNumber.parse("0.7.6.0")

        fun extractVersion(jarName: String): VersionNumber {
            // jarName format: org.jacoco.agent-<version>.jar
            val versionStart = "org.jacoco.agent-".length
            val versionEnd = jarName.length - ".jar".length
            return VersionNumber.parse(jarName.substring(versionStart, versionEnd))
        }
    }
}
