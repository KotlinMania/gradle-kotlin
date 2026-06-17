/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.plugins.antlr.internal.antlr2

import java.io.File

/**
 * Models information relevant to generation of a particular Antlr grammar file.
 */
class GenerationPlan
/**
 * Instantiates a generation plan.
 *
 * @param source The grammar file.
 * @param generationDirectory The directory into which generated lexers and parsers should be written, accounting for
 * declared package.
 */ internal constructor(val source: File?, val generationDirectory: File?) {
    var importVocabTokenTypesDirectory: File? = null

    /**
     * Is the grammar file modeled by this plan out of considered out of date?
     *
     * @return True if the grammar generation is out of date (needs regen); false otherwise.
     */
    var isOutOfDate: Boolean = false
        private set

    val id: String
        get() = this.source!!.getPath()

    /**
     * Marks the plan as out of date.
     */
    fun markOutOfDate() {
        this.isOutOfDate = true
    }
}
