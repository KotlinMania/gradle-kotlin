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
import com.google.common.base.Optional
import com.google.common.base.Preconditions

/**
 * File referenced by a build phase, unique to each build phase.
 *
 * Contains a dictionary [.settings] which holds additional information to be interpreted by
 * the particular phase referencing this object, e.g.:
 *
 * - [PBXSourcesBuildPhase] may read `{"COMPILER_FLAGS": "-foo"}` and interpret
 * that this file should be compiled with the additional flag `"-foo" `.
 */
class PBXBuildFile(fileRef: PBXReference?) : PBXProjectItem() {
    val fileRef: PBXReference
    var settings: Optional<NSDictionary?>

    init {
        this.fileRef = Preconditions.checkNotNull<PBXReference>(fileRef)
        this.settings = Optional.absent<NSDictionary?>()
    }

    override fun isa(): String {
        return "PBXBuildFile"
    }

    override fun stableHash(): Int {
        return fileRef.stableHash()
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        s.addField("fileRef", fileRef)
        if (settings.isPresent()) {
            s.addField("settings", settings.get())
        }
    }

    override fun toString(): String {
        return String.format("%s fileRef=%s settings=%s", super.toString(), fileRef, settings)
    }
}
