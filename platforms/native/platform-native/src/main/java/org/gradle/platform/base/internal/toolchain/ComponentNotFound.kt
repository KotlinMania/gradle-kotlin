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
package org.gradle.platform.base.internal.toolchain

import org.gradle.internal.logging.text.DiagnosticsVisitor

class ComponentNotFound<T> : SearchResult<T?> {
    private val message: String
    private val locations: MutableCollection<String>

    constructor(message: String) {
        this.message = message
        this.locations = mutableListOf<String>()
    }

    constructor(message: String, locations: MutableCollection<String>) {
        this.message = message
        this.locations = locations
    }

    override fun getComponent(): T? {
        return null
    }

    override fun isAvailable(): Boolean {
        return false
    }

    override fun explain(visitor: DiagnosticsVisitor) {
        visitor.node(message)
        if (!locations.isEmpty()) {
            visitor.startChildren()
            for (location in locations) {
                visitor.node(location)
            }
            visitor.endChildren()
        }
    }
}
