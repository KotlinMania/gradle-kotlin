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
package org.gradle.ide.xcode.internal.xcodeproj

import com.dd.plist.NSDictionary
import com.google.common.base.Function
import com.google.common.base.Preconditions
import com.google.common.collect.Ordering
import java.util.Collections

/**
 * The root object representing the project itself.
 */
class PBXProject(name: String?) : PBXContainer() {
    val mainGroup: PBXGroup
    val targets: MutableList<PBXTarget?>
    val buildConfigurationList: XCConfigurationList
    val compatibilityVersion: String = "Xcode 3.2"
    val name: String

    init {
        this.name = Preconditions.checkNotNull<String>(name)
        this.mainGroup = PBXGroup("mainGroup", null, PBXReference.SourceTree.GROUP)
        this.targets = ArrayList<PBXTarget?>()
        this.buildConfigurationList = XCConfigurationList()
    }

    override fun isa(): String {
        return "PBXProject"
    }

    override fun stableHash(): Int {
        return name.hashCode()
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        s.addField("mainGroup", mainGroup)

        Collections.sort<PBXTarget?>(targets, Ordering.natural<Comparable<*>?>().onResultOf<PBXTarget?>(object : Function<PBXTarget?, String?> {
            override fun apply(input: PBXTarget): String? {
                return input.getName()
            }
        }))
        s.addField("targets", targets)
        s.addField("buildConfigurationList", buildConfigurationList)
        s.addField("compatibilityVersion", compatibilityVersion)

        val d = NSDictionary()
        d.put("LastUpgradeCheck", "0610")

        s.addField("attributes", d)
    }
}
