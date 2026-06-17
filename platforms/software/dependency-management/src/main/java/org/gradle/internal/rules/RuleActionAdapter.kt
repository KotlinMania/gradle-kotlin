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
package org.gradle.internal.rules

import groovy.lang.Closure
import org.gradle.api.Action

interface RuleActionAdapter {
    fun <T> createFromClosure(subjectType: Class<T?>?, closure: Closure<*>?): RuleAction<in T?>?

    fun <T> createFromAction(action: Action<in T?>?): RuleAction<in T?>?

    @Deprecated("")
    fun <T> createFromRuleSource(subjectType: Class<T?>?, ruleSource: Any?): RuleAction<in T?>?
}
