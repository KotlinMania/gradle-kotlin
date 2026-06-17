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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.TypedNotationConverter
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.util.internal.GUtil

class ModuleSelectorStringNotationConverter(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory) : TypedNotationConverter<String, ComponentSelector>(String::class.java) {
    /**
     * Empty String for either group or module name is not allowed.
     */
    override fun parseType(notation: String): ComponentSelector {
        checkNotNull(notation)
        val split = notation.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (split.size < 2 || split.size > 3) {
            throw UnsupportedNotationException(notation)
        }
        val group: String = validate(split[0].trim { it <= ' ' }, notation)
        val name: String = validate(split[1].trim { it <= ' ' }, notation)

        if (split.size == 2) {
            return UnversionedModuleComponentSelector(moduleIdentifierFactory.module(group, name)!!)
        }
        val version = split[2].trim { it <= ' ' }
        if (!GUtil.isTrue(version)) {
            throw UnsupportedNotationException(notation)
        }
        return DefaultModuleComponentSelector.newSelector(moduleIdentifierFactory.module(group, name)!!, DefaultImmutableVersionConstraint.of(version))
    }

    override fun describe(visitor: DiagnosticsVisitor) {
        visitor.candidate("String describing the module in 'group:name' format").example("'org.gradle:gradle-core'.")
        visitor.candidate("String describing the selector in 'group:name:version' format").example("'org.gradle:gradle-core:1.+'.")
    }
}
