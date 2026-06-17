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
import com.google.common.base.Preconditions
import org.gradle.api.Named

open class PBXBuildStyle(name: String?) : PBXProjectItem(), Named {
    private val name: String
    var buildSettings: NSDictionary?

    init {
        this.name = Preconditions.checkNotNull<String>(name)
        this.buildSettings = NSDictionary()
    }

    override fun getName(): String {
        return name
    }

    override fun isa(): String? {
        return "PBXBuildStyle"
    }

    override fun stableHash(): Int {
        return name.hashCode()
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        s.addField("name", name)
        s.addField("buildSettings", buildSettings)
    }
}
