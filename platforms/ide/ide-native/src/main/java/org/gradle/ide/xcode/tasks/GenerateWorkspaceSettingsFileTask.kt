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

import com.dd.plist.NSDictionary
import org.gradle.api.Incubating
import org.gradle.api.internal.PropertyListTransformer
import org.gradle.ide.xcode.tasks.internal.XcodeWorkspaceSettingsFile
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.plugins.ide.api.PropertyListGeneratorTask
import org.gradle.work.DisableCachingByDefault

/**
 * Task for generating a Xcode workspace settings file (e.g. `Foo.xcodeproj/project.xcworkspace/xcshareddata/WorkspaceSettings.xcsettings`).
 *
 *
 * This task is used in conjunction with [GenerateXcodeWorkspaceFileTask].
 *
 * @see org.gradle.ide.xcode.XcodeWorkspace
 *
 * @since 4.2
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateWorkspaceSettingsFileTask : PropertyListGeneratorTask<XcodeWorkspaceSettingsFile?>() {
    override fun configure(settingsFile: XcodeWorkspaceSettingsFile?) {
    }

    override fun create(): XcodeWorkspaceSettingsFile {
        return XcodeWorkspaceSettingsFile(uncheckedNonnullCast<PropertyListTransformer<NSDictionary?>?>(getPropertyListTransformer()))
    }
}
