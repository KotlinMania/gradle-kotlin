/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import groovy.util.Node
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory

/**
 * A variable library entry.
 */
class Variable : AbstractLibrary {
    constructor(node: Node, fileReferenceFactory: FileReferenceFactory) : super(node, fileReferenceFactory) {
        setSourcePath(fileReferenceFactory.fromVariablePath(node.attribute("sourcepath") as String?))
    }

    constructor(library: FileReference) : super(library)

    override fun getKind(): String {
        return "var"
    }

    override fun toString(): String {
        return "Variable" + super.toString()
    }
}
