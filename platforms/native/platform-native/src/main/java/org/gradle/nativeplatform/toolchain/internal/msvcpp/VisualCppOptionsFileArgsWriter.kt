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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.internal.process.ArgWriter
import org.gradle.nativeplatform.toolchain.internal.OptionsFileArgsWriter
import java.io.File

/**
 * Uses an option file for arguments passed to Visual C++.
 */
internal class VisualCppOptionsFileArgsWriter(tempDir: File?) : OptionsFileArgsWriter(tempDir) {
    override fun transformArgs(originalArgs: MutableList<String?>, tempDir: File?): MutableList<String?>? {
        return ArgWriter.windowsStyle().generateArgsFile(originalArgs, File(tempDir, "options.txt"))
    }
}
