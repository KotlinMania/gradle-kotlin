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
package org.gradle.ide.xcode.internal

import org.apache.commons.lang3.StringUtils
import org.gradle.util.internal.CollectionUtils.collect
import java.io.File
import java.util.Arrays
import java.util.function.Function

object XcodeUtils {
    fun toSpaceSeparatedList(vararg files: File?): String? {
        return toSpaceSeparatedList(Arrays.asList<File?>(*files))
    }

    fun toSpaceSeparatedList(it: Iterable<File?>): String? {
        return StringUtils.join(collect<String?, File?>(it, Function { file: File? -> quote(file!!.getAbsolutePath()) }), ' ')
    }

    private fun quote(value: String): String {
        return "\"" + value + "\""
    }
}
