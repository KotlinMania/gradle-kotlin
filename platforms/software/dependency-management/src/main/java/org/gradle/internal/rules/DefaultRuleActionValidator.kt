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

import org.gradle.model.internal.type.ModelType
import java.util.Arrays
import java.util.stream.Collectors

class DefaultRuleActionValidator : RuleActionValidator {
    private val validInputType: MutableList<Class<*>?>

    constructor() {
        this.validInputType = mutableListOf<Class<*>?>()
    }

    constructor(vararg validInputTypes: Class<*>?) {
        this.validInputType = Arrays.asList<Class<*>?>(*validInputTypes)
    }

    override fun <T> validate(ruleAction: RuleAction<in T?>): RuleAction<in T?> {
        validateInputTypes(ruleAction)
        return ruleAction
    }

    private fun validateInputTypes(ruleAction: RuleAction<*>) {
        for (inputType in ruleAction.getInputTypes()) {
            if (!validInputType.contains(inputType)) {
                throw RuleActionValidationException(invalidParameterMessage(inputType))
            }
        }
    }

    private fun invalidParameterMessage(inputType: Class<*>): String {
        if (validInputType.isEmpty()) {
            return String.format(VALID_NO_TYPES, inputType.getName())
        } else {
            return String.format(VALID_MULTIPLE_TYPES, inputType.getName(), validTypeNames())
        }
    }

    private fun validTypeNames(): String {
        return validInputType.stream().map { clazz: Class<*>? -> ModelType.of(clazz) }.map<String?> { obj: ModelType<Any?>? -> obj.toString() }.collect(Collectors.joining(" or "))
    }

    companion object {
        private const val VALID_NO_TYPES = "Rule may not have an input parameter of type: %s."
        private const val VALID_MULTIPLE_TYPES = "Rule may not have an input parameter of type: %s. Second parameter must be of type: %s."
    }
}
