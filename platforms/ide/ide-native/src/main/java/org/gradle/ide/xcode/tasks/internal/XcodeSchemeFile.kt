/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.ide.xcode.tasks.internal

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Action
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

class XcodeSchemeFile(xmlTransformer: XmlTransformer) : XmlPersistableConfigurationObject(xmlTransformer) {
    val buildAction: BuildAction
        get() = XcodeSchemeFile.BuildAction(Companion.getOrAppendNode(xml!!, "BuildAction")!!)

    val testAction: TestAction
        get() = XcodeSchemeFile.TestAction(Companion.getOrAppendNode(xml!!, "TestAction")!!)

    val launchAction: LaunchAction
        get() = XcodeSchemeFile.LaunchAction(Companion.getOrAppendNode(xml!!, "LaunchAction")!!)

    val profileAction: ProfileAction
        get() = XcodeSchemeFile.ProfileAction(Companion.getOrAppendNode(xml!!, "ProfileAction")!!)

    val archiveAction: ArchiveAction
        get() = XcodeSchemeFile.ArchiveAction(Companion.getOrAppendNode(xml!!, "ArchiveAction")!!)

    val analyzeAction: AnalyzeAction
        get() = XcodeSchemeFile.AnalyzeAction(Companion.getOrAppendNode(xml!!, "AnalyzeAction")!!)

    val defaultResourceName: String
        get() = "default.xcscheme"

    class BuildAction internal constructor(private val xml: Node) {
        fun entry(action: Action<BuildActionEntry?>) {
            action.execute(BuildActionEntry(getOrAppendNode(xml, "BuildActionEntries")!!.appendNode("BuildActionEntry")))
        }
    }

    class BuildActionEntry internal constructor(private val xml: Node) {
        fun setBuildForRunning(buildForRunning: Boolean) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildForRunning", toYesNo(buildForRunning))
        }

        fun setBuildForTesting(buildForTesting: Boolean) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildForTesting", toYesNo(buildForTesting))
        }

        fun setBuildForProfiling(buildForProfiling: Boolean) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildForProfiling", toYesNo(buildForProfiling))
        }

        fun setBuildForArchiving(buildForArchiving: Boolean) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildForArchiving", toYesNo(buildForArchiving))
        }

        fun setBuildForAnalysing(buildForAnalysing: Boolean) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildForAnalyzing", toYesNo(buildForAnalysing))
        }

        fun setBuildableReference(buildableReference: BuildableReference) {
            xml.append(buildableReference.toXml())
        }
    }

    class TestAction internal constructor(private val xml: Node) {
        fun setBuildConfiguration(buildConfiguration: String?) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildConfiguration", buildConfiguration)
        }

        fun entry(action: Action<TestableEntry?>) {
            action.execute(TestableEntry(getOrAppendNode(xml, "Testables")!!.appendNode("TestableReference")))
        }
    }

    class TestableEntry internal constructor(private val xml: Node) {
        fun setSkipped(skipped: Boolean) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("skipped", toYesNo(skipped))
        }

        fun setBuildableReference(buildableReference: BuildableReference) {
            xml.append(buildableReference.toXml())
        }
    }

    class LaunchAction internal constructor(private val xml: Node) {
        fun setBuildConfiguration(buildConfiguration: String?) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildConfiguration", buildConfiguration)
        }

        fun setBuildableProductRunnable(buildableReference: BuildableReference) {
            xml.appendNode("BuildableProductRunnable").append(buildableReference.toXml())
        }

        fun setBuildableReference(buildableReference: BuildableReference) {
            xml.append(buildableReference.toXml())
        }
    }

    class ProfileAction internal constructor(private val xml: Node) {
        fun setBuildConfiguration(buildConfiguration: String?) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildConfiguration", buildConfiguration)
        }
    }

    class AnalyzeAction internal constructor(private val xml: Node) {
        fun setBuildConfiguration(buildConfiguration: String?) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildConfiguration", buildConfiguration)
        }
    }

    class ArchiveAction internal constructor(private val xml: Node) {
        fun setBuildConfiguration(buildConfiguration: String?) {
            val attributes = uncheckedNonnullCast<MutableMap<String?, String?>?>(xml.attributes())
            attributes!!.put("buildConfiguration", buildConfiguration)
        }
    }

    class BuildableReference {
        var containerRelativePath: String? = null
        var buildableIdentifier: String? = null
        var blueprintIdentifier: String? = null
        var buildableName: String? = null
        var blueprintName: String? = null

        fun toXml(): Node {
            val attributes: MutableMap<String?, String?> = HashMap<String?, String?>()
            attributes.put("BuildableIdentifier", this.buildableIdentifier)
            attributes.put("BlueprintIdentifier", this.blueprintIdentifier)
            attributes.put("BuildableName", this.buildableName)
            attributes.put("BlueprintName", this.blueprintName)
            attributes.put("ReferencedContainer", "container:" + this.containerRelativePath)
            return Node(null, "BuildableReference", attributes)
        }
    }

    companion object {
        private fun getOrAppendNode(xml: Node, name: String?): Node? {
            val nodes = xml.get(name) as NodeList
            if (nodes.isEmpty()) {
                return xml.appendNode(name)
            }
            return nodes.get(0) as Node?
        }

        private const val YES = "YES"
        private const val NO = "NO"
        private fun toYesNo(value: Boolean): String {
            if (value) {
                return YES
            }
            return NO
        }
    }
}
