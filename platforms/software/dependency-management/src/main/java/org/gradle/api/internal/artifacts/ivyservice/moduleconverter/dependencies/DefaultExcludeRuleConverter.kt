/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.util.internal.GUtil

class DefaultExcludeRuleConverter(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory) : ExcludeRuleConverter {
    override fun convertExcludeRule(excludeRule: ExcludeRule): DefaultExclude {
        return createExcludeRule(excludeRule.getGroup(), excludeRule.getModule())
    }

    override fun createExcludeRule(group: String, module: String): DefaultExclude {
        var group = group
        var module = module
        group = GUtil.elvis<String>(group, PatternMatchers.ANY_EXPRESSION)!!
        module = GUtil.elvis<String>(module, PatternMatchers.ANY_EXPRESSION)!!
        return DefaultExclude(moduleIdentifierFactory.module(group, module)!!)
    }
}
