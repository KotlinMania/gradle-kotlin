/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.base.internal.registry

import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.TransformationFileType

/**
 * A registered language transformation.
 */
interface LanguageTransform<U : LanguageSourceSet?, V : TransformationFileType?> {
    /**
     * The language name.
     */
    val languageName: String?

    /**
     * The interface type of the language source set.
     */
    val sourceSetType: Class<U?>?

    /**
     * The output type generated from these language sources.
     */
    val outputType: Class<V?>?

    /**
     * The tool extensions that should be added to any binary with these language sources.
     */
    val binaryTools: MutableMap<String?, Class<*>?>?

    /**
     * The task used to transform sources into code for the target runtime.
     */
    val transformTask: SourceTransformTaskConfig?

    fun applyToBinary(binary: BinarySpec?): Boolean
}
