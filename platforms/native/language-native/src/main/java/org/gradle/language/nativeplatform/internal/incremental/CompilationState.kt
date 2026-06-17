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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableMap
import java.io.File

/**
 * An immutable snapshot of compilation state.
 */
class CompilationState {
    val fileStates: ImmutableMap<File?, SourceFileState?>

    constructor(fileStates: ImmutableMap<File?, SourceFileState?>) {
        this.fileStates = fileStates
    }

    constructor() {
        fileStates = ImmutableMap.of<File?, SourceFileState?>()
    }

    val sourceInputs: MutableSet<File?>
        get() = fileStates.keys

    fun getState(file: File?): SourceFileState? {
        return fileStates.get(file)
    }
}
