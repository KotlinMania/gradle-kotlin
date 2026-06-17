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
package org.gradle.swiftpm.internal

import org.gradle.language.swift.SwiftVersion
import org.gradle.swiftpm.Package
import java.io.Serializable

class DefaultPackage(
    private val products: MutableSet<AbstractProduct?>?,
    val targets: MutableList<DefaultTarget?>?,
    val dependencies: MutableList<Dependency?>?,
    val swiftLanguageVersion: SwiftVersion?
) : Package, Serializable {
    override fun getProducts(): MutableSet<AbstractProduct?>? {
        return products
    }
}
