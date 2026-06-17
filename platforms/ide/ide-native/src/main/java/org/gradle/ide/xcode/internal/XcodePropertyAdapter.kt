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

import org.gradle.api.Project
import org.gradle.util.internal.GUtil
import java.util.Arrays

class XcodePropertyAdapter(private val project: Project) {
    val action: String?
        get() = getXcodeProperty("ACTION")

    val productName: String?
        get() = getXcodeProperty("PRODUCT_NAME")

    val configuration: String?
        get() = getXcodeProperty("CONFIGURATION")

    val builtProductsDir: String?
        get() = getXcodeProperty("BUILT_PRODUCTS_DIR")

    private fun getXcodeProperty(name: String): String? {
        return GUtil.elvis<Any?>(project.findProperty(prefixName(name)), "").toString()
    }

    companion object {
        val adapterCommandLine: MutableList<String?>
            get() = Arrays.asList<String?>(
                toGradleProperty("ACTION"),
                toGradleProperty("PRODUCT_NAME"),
                toGradleProperty("CONFIGURATION"),
                toGradleProperty("BUILT_PRODUCTS_DIR")
            )

        private fun toGradleProperty(source: String): String {
            return "-P" + prefixName(source) + "=\"\${" + source + "}\""
        }

        private fun prefixName(source: String): String {
            return "org.gradle.internal.xcode.bridge." + source
        }
    }
}
