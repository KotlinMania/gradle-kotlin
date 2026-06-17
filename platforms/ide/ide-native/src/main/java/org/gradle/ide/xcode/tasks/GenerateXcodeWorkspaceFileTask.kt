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
package org.gradle.ide.xcode.tasks

import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.ide.xcode.tasks.internal.XcodeWorkspaceFile
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.work.DisableCachingByDefault

/**
 * Task for generating a Xcode workspace file (e.g. `Foo.xcworkspace/contents.xcworkspacedata`). A workspace can contain any number of Xcode projects.
 *
 * @see org.gradle.ide.xcode.XcodeWorkspace
 *
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateXcodeWorkspaceFileTask : XmlGeneratorTask<XcodeWorkspaceFile?>() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    var xcodeProjectLocations: FileCollection

    init {
        this.xcodeProjectLocations = getProject().files()
    }

    override fun configure(workspaceFile: XcodeWorkspaceFile) {
        for (xcodeProjectDir in xcodeProjectLocations) {
            workspaceFile.addLocation(xcodeProjectDir.getAbsolutePath())
        }
    }

    override fun create(): XcodeWorkspaceFile? {
        return XcodeWorkspaceFile(xmlTransformer)
    }

    val inputFile: File?
        get() = null
}
