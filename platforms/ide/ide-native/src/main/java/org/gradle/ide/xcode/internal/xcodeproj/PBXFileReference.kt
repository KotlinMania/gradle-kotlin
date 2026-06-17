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

import com.google.common.base.Optional
import com.google.common.io.Files

/**
 * Reference to a concrete file.
 */
class PBXFileReference(name: String, path: String?, sourceTree: SourceTree?) : PBXReference(name, path, sourceTree) {
    var explicitFileType: Optional<String?>
    private val lastKnownFileType: Optional<String?>

    init {
        // this is necessary to prevent O(n^2) behavior in xcode project loading
        val fileType: String? = FileTypes.Companion.FILE_EXTENSION_TO_UTI.get(Files.getFileExtension(name))
        explicitFileType = Optional.fromNullable<String?>(fileType)
        lastKnownFileType = Optional.absent<String?>()
    }

    override fun isa(): String {
        return "PBXFileReference"
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        if (explicitFileType.isPresent()) {
            s.addField("explicitFileType", explicitFileType.get())
        }

        if (lastKnownFileType.isPresent()) {
            s.addField("lastKnownFileType", lastKnownFileType.get())
        }
    }

    override fun toString(): String {
        return String.format(
            "%s explicitFileType=%s",
            super.toString(),
            this.explicitFileType
        )
    }
}
