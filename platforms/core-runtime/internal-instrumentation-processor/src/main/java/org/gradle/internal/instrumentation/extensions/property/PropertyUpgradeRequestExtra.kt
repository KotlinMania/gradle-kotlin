/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.extensions.property

import com.squareup.javapoet.TypeName
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader.DeprecationSpec
import org.gradle.internal.instrumentation.model.RequestExtra
import javax.lang.model.element.ExecutableElement

internal class PropertyUpgradeRequestExtra(
    val propertyName: String?,
    val methodName: String?,
    val methodDescriptor: String?,
    val returnType: TypeName?,
    val implementationClassName: String?,
    val interceptedPropertyName: String?,
    val interceptedPropertyAccessorName: String?,
    val newPropertyType: TypeName?,
    val deprecationSpec: DeprecationSpec?,
    val binaryCompatibility: ReplacesEagerProperty.BinaryCompatibility?,
    val bridgedMethodInfo: BridgedMethodInfo?
) : RequestExtra {
    class BridgedMethodInfo(val bridgedMethod: ExecutableElement?, val bridgeType: BridgeType?) {
        enum class BridgeType {
            ADAPTER_METHOD_BRIDGE,
            INSTANCE_METHOD_BRIDGE
        }
    }
}
