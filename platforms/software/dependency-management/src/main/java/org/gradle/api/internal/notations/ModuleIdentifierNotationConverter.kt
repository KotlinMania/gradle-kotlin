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
package org.gradle.api.internal.notations

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.internal.typeconversion.UnsupportedNotationException

class ModuleIdentifierNotationConverter(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory) : NotationConverter<String?, ModuleIdentifier?> {
    /**
     * Empty String for either group or module name is not allowed.
     */
    @Throws(TypeConversionException::class)
    override fun convert(notation: String, result: NotationConvertResult<in ModuleIdentifier?>) {
        checkNotNull(notation)
        val split: Array<String?> = notation.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (split.size != 2) {
            throw UnsupportedNotationException(notation)
        }
        val group = ModuleNotationValidation.validate(split[0]!!.trim { it <= ' ' }, notation)
        val name = ModuleNotationValidation.validate(split[1]!!.trim { it <= ' ' }, notation)
        result.converted(moduleIdentifierFactory.module(group, name))
    }

    override fun describe(visitor: DiagnosticsVisitor) {
        visitor.candidate("String describing the module in 'group:name' format").example("'org.gradle:gradle-core'")
    }
}
