/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.service

import java.lang.reflect.Method
import java.lang.reflect.Type

internal abstract class AbstractServiceMethod(target: Method) : ServiceMethod {
    private val method: Method?
    private val owner: Class<*>
    private val name: String
    private val parameterTypes: Array<Type?>
    private val serviceType: Type

    init {
        this.method = target
        this.owner = target.getDeclaringClass()
        this.name = target.getName()
        this.parameterTypes = target.getGenericParameterTypes()
        this.serviceType = target.getGenericReturnType()
    }

    override fun getServiceType(): Type {
        return serviceType
    }

    override fun getParameterTypes(): Array<Type?> {
        return parameterTypes
    }

    override fun getOwner(): Class<*> {
        return owner
    }

    override fun getName(): String {
        return name
    }

    override fun getMethod(): Method? {
        return method
    }
}
