/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.buildinit.plugins.internal.modifiers

enum class BuildInitTestFramework(displayName: String) : WithIdentifier {
    NONE("none"),
    JUNIT("JUnit 4"),
    TESTNG("TestNG"),
    SPOCK("Spock"),
    KOTLINTEST("kotlin.test"),
    SCALATEST("ScalaTest"),
    JUNIT_JUPITER("JUnit Jupiter"),
    XCTEST("XCTest"),
    CPPTest("C++ executable");

    private val displayName: String?

    init {
        this.displayName = displayName
    }

    override fun getId(): String {
        return Names.idFor(this)
    }

    override fun toString(): String {
        return displayName!!
    }

    companion object {
        fun listSupported(): MutableList<String?> {
            val result: MutableList<String?> = ArrayList<String?>()
            for (testFramework in entries) {
                if (testFramework != BuildInitTestFramework.NONE) {
                    result.add(testFramework.getId())
                }
            }
            return result
        }
    }
}
