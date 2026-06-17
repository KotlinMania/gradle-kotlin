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

import com.google.common.base.CharMatcher
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import org.gradle.api.Named

/**
 * Superclass for file, directories, and groups. Xcode's virtual file hierarchy are made of these
 * objects.
 */
open class PBXReference(name: String?, var path: String?, sourceTree: SourceTree?) : PBXContainerItem(), Named {
    private val name: String

    /**
     * The "base" path of the reference. The absolute path is resolved by prepending the resolved
     * base path.
     */
    var sourceTree: SourceTree

    init {
        this.name = Preconditions.checkNotNull<String>(name)
        this.sourceTree = Preconditions.checkNotNull<SourceTree>(sourceTree)
    }

    override fun getName(): String {
        return name
    }

    override fun isa(): String? {
        return "PBXReference"
    }

    override fun stableHash(): Int {
        return name.hashCode()
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        s.addField("name", name)
        if (path != null) {
            s.addField("path", path)
        }
        s.addField("sourceTree", sourceTree.toString())
    }

    override fun toString(): String {
        return String.format(
            "%s name=%s path=%s sourceTree=%s",
            super.toString(),
            getName(),
            this.path,
            this.sourceTree
        )
    }

    enum class SourceTree(str: String) {
        /**
         * Relative to the path of the group containing this.
         */
        GROUP("<group>"),

        /**
         * Absolute system path.
         */
        ABSOLUTE("<absolute>"),

        /**
         * Relative to the build setting `BUILT_PRODUCTS_DIR`.
         */
        BUILT_PRODUCTS_DIR("BUILT_PRODUCTS_DIR"),

        /**
         * Relative to the build setting `SDKROOT`.
         */
        SDKROOT("SDKROOT"),

        /**
         * Relative to the directory containing the project file `SOURCE_ROOT`.
         */
        SOURCE_ROOT("SOURCE_ROOT"),

        /**
         * Relative to the Developer content directory inside the Xcode application
         * (e.g. `/Applications/Xcode.app/Contents/Developer`).
         */
        DEVELOPER_DIR("DEVELOPER_DIR"),
        ;

        private val rep: String?

        init {
            rep = str
        }

        override fun toString(): String {
            return rep!!
        }

        companion object {
            /**
             * Return a sourceTree given a build setting that is typically used as a source tree prefix.
             *
             * The build setting may be optionally prefixed by '$' which will be stripped.
             */
            fun fromBuildSetting(buildSetting: String): Optional<SourceTree?> {
                val data = CharMatcher.`is`('$').trimLeadingFrom(buildSetting)
                when (data) {
                    "BUILT_PRODUCTS_DIR" -> return Optional.of<SourceTree?>(SourceTree.BUILT_PRODUCTS_DIR)
                    "SDKROOT" -> return Optional.of<SourceTree?>(SourceTree.SDKROOT)
                    "SOURCE_ROOT" -> return Optional.of<SourceTree?>(SourceTree.SOURCE_ROOT)
                    "DEVELOPER_DIR" -> return Optional.of<SourceTree?>(SourceTree.DEVELOPER_DIR)
                    else -> return Optional.absent<SourceTree?>()
                }
            }
        }
    }
}
