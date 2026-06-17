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
package org.gradle.language.swift.internal

import org.gradle.api.file.FileCollection
import org.gradle.language.internal.DefaultBinaryCollection
import org.gradle.language.nativeplatform.internal.ComponentWithNames
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftComponent

abstract class DefaultSwiftComponent<T : SwiftBinary?> @JvmOverloads constructor(name: String, binaryType: Class<out SwiftBinary?> = SwiftBinary::class.java) : DefaultNativeComponent(),
    SwiftComponent, ComponentWithNames {
    private val binaries: DefaultBinaryCollection<T?>
    private val swiftSource: FileCollection?
    private val name: String?
    private val names: Names

    init {
        this.name = name
        this.swiftSource = createSourceView("src/" + name + "/swift", mutableListOf<String?>("swift"))
        this.names = Names.Companion.of(name)
        this.binaries = org.gradle.internal.Cast.uncheckedCast<DefaultBinaryCollection<T?>?>(
            getObjectFactory().newInstance<org.gradle.language.internal.DefaultBinaryCollection<*>?>(
                org.gradle.language.internal.DefaultBinaryCollection::class.java,
                binaryType
            )
        )!!
    }

    override fun getName(): String? {
        return name
    }

    override fun getNames(): Names {
        return names
    }

    override fun getSwiftSource(): FileCollection? {
        return swiftSource
    }

    override fun getBinaries(): DefaultBinaryCollection<T?> {
        return binaries
    }
}
