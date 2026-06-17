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
package org.gradle.api.internal.artifacts.dsl

import org.apache.commons.lang3.StringUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.capabilities.Capability
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.internal.typeconversion.TypedNotationConverter

class CapabilityNotationParserFactory(private val versionIsRequired: Boolean) : Factory<NotationParser<Any, Capability>?> {
    override fun create(): CapabilityNotationParser {
        // Currently the converter is stateless, doesn't need any external context, so for performance we return a singleton
        return if (versionIsRequired) STRICT_CONVERTER else LENIENT_CONVERTER
    }

    private class StringNotationParser(private val versionIsRequired: Boolean) : TypedNotationConverter<CharSequence, Capability>(CharSequence::class.java) {
        override fun parseType(notation: CharSequence): Capability {
            val stringNotation = notation.toString()
            val parts = stringNotation.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size != 3) {
                if (versionIsRequired || parts.size != 2) {
                    reportInvalidNotation(stringNotation)
                }
            }
            for (part in parts) {
                if (StringUtils.isEmpty(part)) {
                    reportInvalidNotation(stringNotation)
                }
            }
            val version = if (parts.size == 3) parts[2] else null
            return DefaultImmutableCapability(parts[0], parts[1], version)
        }

        companion object {
            private fun reportInvalidNotation(notation: String) {
                throw InvalidUserDataException(
                    ("Invalid format for capability: '" + notation + "'. The correct notation is a 3-part group:name:version notation, "
                            + "e.g: 'org.group:capability:1.0'")
                )
            }
        }
    }

    private class StrictCapabilityMapNotationParser : MapNotationConverter<Capability>() {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Maps").example("[group: 'org.group', name: 'capability', version: '1.0']")
        }

        @Suppress("unused") // reflection
        protected fun parseMap(
            @MapKey("group") group: String,
            @MapKey("name") name: String,
            @MapKey("version") version: String
        ): Capability {
            return DefaultImmutableCapability(group, name, version)
        }
    }

    private class LenientCapabilityMapNotationParser : MapNotationConverter<Capability>() {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Maps").example("[group: 'org.group', name: 'capability', version: '1.0']")
        }

        @Suppress("unused") // reflection
        protected fun parseMap(
            @MapKey("group") group: String,
            @MapKey("name") name: String,
            @MapKey("version") version: String?
        ): Capability {
            return DefaultImmutableCapability(group, name, version)
        }
    }

    companion object {
        private val STRICT_CONVERTER: CapabilityNotationParser = createSingletonConverter(true)
        private val LENIENT_CONVERTER: CapabilityNotationParser = createSingletonConverter(false)

        private fun createSingletonConverter(strict: Boolean): CapabilityNotationParser {
            val parser = NotationParserBuilder.toType<Capability>(Capability::class.java)
                .converter(StringNotationParser(strict))
                .converter(if (strict) StrictCapabilityMapNotationParser() else LenientCapabilityMapNotationParser())
                .toComposite()
            return object : CapabilityNotationParser {
                @Throws(TypeConversionException::class)
                override fun parseNotation(notation: Any): Capability {
                    return parser.parseNotation(notation)
                }

                override fun describe(visitor: DiagnosticsVisitor) {
                    parser.describe(visitor)
                }
            }
        }
    }
}
