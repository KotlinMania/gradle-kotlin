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
package org.gradle.buildinit.plugins.internal

import org.gradle.api.Action

interface ScriptBlockBuilder {
    /**
     * Adds a property assignment statement to this block
     */
    fun propertyAssignment(comment: String?, propertyName: String, propertyValue: Any, assignOperator: Boolean)

    /**
     * Adds a method invocation statement to this block
     */
    fun methodInvocation(comment: String?, methodName: String, vararg methodArgs: Any)

    /**
     * Adds a method invocation statement to this block
     */
    fun methodInvocation(comment: String?, target: BuildScriptBuilder.Expression, methodName: String, vararg methodArgs: Any)

    /**
     * Adds a statement to this block.
     */
    fun statement(comment: String?, statement: BuildScriptBuilder.Statement)

    /**
     * Adds a block statement to this block.
     *
     * @return The body of the block, to which further statements can be added.
     */
    fun block(comment: String?, methodName: String): ScriptBlockBuilder?

    /**
     * Adds a block statement to this block.
     */
    fun block(comment: String?, methodName: String, blockContentsBuilder: Action<in ScriptBlockBuilder>)

    /**
     * Adds an element to the given container.
     *
     * @return an expression that can be used to refer to the element. Note: currently this expression can only be used within this current block.
     */
    fun containerElement(comment: String?, container: String, elementName: String, elementType: String?, blockContentsBuilder: Action<in ScriptBlockBuilder>): BuildScriptBuilder.Expression?

    /**
     * Returns a property expression that can be used as a method argument or property assignment value
     */
    fun propertyExpression(value: String): BuildScriptBuilder.Expression?

    /**
     * Adds a single line comment to this block.
     *
     * @param comment the comment to add
     */
    fun comment(comment: String)
}
