/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.problems.internal

import com.google.common.base.Objects
import java.io.Serializable

class DefaultTypeValidationData(
    private val pluginId: String,
    private val propertyName: String,
    private val functionName: String,
    private val parentPropertyName: String,
    private val typeName: String
) : TypeValidationData, Serializable {
    override fun getPluginId(): String {
        return pluginId
    }

    override fun getPropertyName(): String {
        return propertyName
    }

    override fun getFunctionName(): String {
        return functionName
    }

    override fun getParentPropertyName(): String {
        return parentPropertyName
    }

    override fun getTypeName(): String {
        return typeName
    }

    override fun equals(o: Any): Boolean {
        if (o !is DefaultTypeValidationData) {
            return false
        }
        val that = o
        return Objects.equal(pluginId, that.pluginId) &&
                Objects.equal(propertyName, that.propertyName) &&
                Objects.equal(functionName, that.functionName) &&
                Objects.equal(parentPropertyName, that.parentPropertyName) &&
                Objects.equal(typeName, that.typeName)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(pluginId, propertyName, functionName, parentPropertyName, typeName)
    }

    private class DefaultTypeValidationDataBuilder : TypeValidationDataSpec, AdditionalDataBuilder<TypeValidationData?> {
        private var pluginId: String? = null
        private var propertyName: String? = null
        private var functionName: String? = null
        private var parentPropertyName: String? = null
        private var typeName: String? = null

        constructor()

        constructor(from: TypeValidationData) {
            this.pluginId = from.getPluginId()
            this.propertyName = from.getPropertyName()
            this.functionName = from.getFunctionName()
            this.parentPropertyName = from.getParentPropertyName()
            this.typeName = from.getTypeName()
        }

        override fun build(): DefaultTypeValidationData {
            return DefaultTypeValidationData(pluginId!!, propertyName!!, functionName!!, parentPropertyName!!, typeName!!)
        }

        override fun pluginId(pluginId: String): TypeValidationDataSpec {
            this.pluginId = pluginId
            return this
        }

        override fun propertyName(propertyName: String): TypeValidationDataSpec {
            this.propertyName = propertyName
            return this
        }

        override fun functionName(functionName: String): TypeValidationDataSpec {
            this.functionName = functionName
            return this
        }

        override fun parentPropertyName(parentPropertyName: String): TypeValidationDataSpec {
            this.parentPropertyName = parentPropertyName
            return this
        }

        override fun typeName(typeName: String): TypeValidationDataSpec {
            this.typeName = typeName
            return this
        }
    }

    companion object {
        fun builder(from: TypeValidationData?): AdditionalDataBuilder<TypeValidationData> {
            if (from == null) {
                return DefaultTypeValidationDataBuilder()
            }
            return DefaultTypeValidationDataBuilder(from)
        }
    }
}
