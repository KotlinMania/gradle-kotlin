/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization

import org.gradle.internal.scripts.DefaultScriptFileResolver
import java.io.File
import java.util.Collections

abstract class DirectoryInitScriptFinder : InitScriptFinder {
    protected fun findScriptsInDir(initScriptsDir: File, scripts: MutableCollection<File?>) {
        if (!initScriptsDir.isDirectory()) {
            return
        }
        val found = initScriptsIn(initScriptsDir)
        Collections.sort<File?>(found)
        scripts.addAll(found)
    }

    protected fun resolveScriptFile(dir: File, basename: String): File? {
        return resolver().resolveScriptFile(dir, basename).getSelectedCandidate()
    }

    private fun initScriptsIn(initScriptsDir: File): MutableList<File?> {
        return resolver().findScriptsIn(initScriptsDir).toMutableList()
    }

    private fun resolver(): DefaultScriptFileResolver {
        return DefaultScriptFileResolver()
    }
}
