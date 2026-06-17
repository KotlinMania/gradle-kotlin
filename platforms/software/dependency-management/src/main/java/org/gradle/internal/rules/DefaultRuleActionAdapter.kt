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
import org.gradle.api.InvalidUserCodeException
import org.gradle.model.internal.type.ModelType

class DefaultRuleActionAdapter(private val ruleActionValidator: RuleActionValidator, private val context: String?) : RuleActionAdapter {
    override fun <T> createFromClosure(subjectType: Class<T?>?, closure: Closure<*>?): RuleAction<in T?>? {
        try {
            return ruleActionValidator.validate<T?>(ClosureBackedRuleAction<T?>(subjectType, closure))
        } catch (e: RuleActionValidationException) {
            throw InvalidUserCodeException(String.format(INVALID_CLOSURE_ERROR, context), e)
        }
    }

    override fun <T> createFromAction(action: Action<in T?>?): RuleAction<in T?>? {
        try {
            return ruleActionValidator.validate<T?>(NoInputsRuleAction<T?>(action))
        } catch (e: RuleActionValidationException) {
            throw InvalidUserCodeException(String.format(INVALID_ACTION_ERROR, context), e)
        }
    }

    @Deprecated("")
    override fun <T> createFromRuleSource(subjectType: Class<T?>?, ruleSource: Any?): RuleAction<in T?>? {
        try {
            return ruleActionValidator.validate<T?>(RuleSourceBackedRuleAction.Companion.create<Any?, T?>(ModelType.of<T?>(subjectType), ruleSource))
        } catch (e: RuleActionValidationException) {
            throw InvalidUserCodeException(String.format(INVALID_RULE_SOURCE_ERROR, context), e)
        }
    }

    companion object {
        private const val INVALID_CLOSURE_ERROR = "The closure provided is not valid as a rule for '%s'."
        private const val INVALID_ACTION_ERROR = "The action provided is not valid as a rule for '%s'."
        private const val INVALID_RULE_SOURCE_ERROR = "The rule source provided does not provide a valid rule for '%s'."
    }
}
