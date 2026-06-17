/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypedNotationConverter

class ComponentIdentifierParserFactory : Factory<NotationParser<Any?, ComponentIdentifier?>?> {
    override fun create(): NotationParser<Any?, ComponentIdentifier?>? {
        return NotationParserBuilder.toType<ComponentIdentifier?>(ComponentIdentifier::class.java)
            .fromCharSequence(StringNotationConverter())
            .converter(ComponentIdentifierMapNotationConverter())
            .toComposite()
    }

    internal class ComponentIdentifierMapNotationConverter : MapNotationConverter<ModuleComponentIdentifier?>() {
        protected fun parseMap(
            @MapKey("group") group: String,
            @MapKey("name") name: String,
            @MapKey("version") version: String
        ): ModuleComponentIdentifier {
            return newId(
                DefaultModuleIdentifier.newId(validate(group.trim { it <= ' ' }), validate(name.trim { it <= ' ' })),
                validate(version.trim { it <= ' ' })
            )
        }
    }

    internal class StringNotationConverter : TypedNotationConverter<String?, ModuleComponentIdentifier?>(String::class.java) {
        override fun parseType(notation: String): ModuleComponentIdentifier {
            val parts: Array<String?> = notation.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size != 3) {
                throw InvalidUserDataException("Invalid module component notation: " + notation + " : must be a valid 3 part identifier, eg.: org.gradle:gradle:1.0")
            }
            return newId(
                DefaultModuleIdentifier.newId(ModuleNotationValidation.validate(parts[0]!!.trim { it <= ' ' }, notation), ModuleNotationValidation.validate(parts[1]!!.trim { it <= ' ' }, notation)),
                ModuleNotationValidation.validate(parts[2]!!.trim { it <= ' ' }, notation)
            )
        }
    }
}
