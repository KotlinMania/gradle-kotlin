/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.ant

import org.apache.tools.ant.types.selectors.FileSelector
import java.io.File

class BaseDirSelector : FileSelector {
    private var baseDir: File? = null

    fun setBaseDir(baseDir: File?) {
        this.baseDir = baseDir
    }

    override fun toString(): String {
        return String.format("{basedir: %s}", baseDir)
    }

    override fun isSelected(baseDir: File, filename: String?, file: File?): Boolean {
        return baseDir == this.baseDir
    }
}
