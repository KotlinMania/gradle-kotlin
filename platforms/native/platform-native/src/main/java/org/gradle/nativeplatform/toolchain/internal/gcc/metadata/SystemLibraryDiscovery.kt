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
package org.gradle.nativeplatform.toolchain.internal.gcc.metadata

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPathLocator

@ServiceScope(Scope.BuildSession::class)
class SystemLibraryDiscovery(private val macOSSdkPathLocator: MacOSSdkPathLocator) {
    fun compilerProbeArgs(target: NativePlatformInternal): Array<String?> {
        if (!target.getOperatingSystem().isMacOsX) {
            return arrayOfNulls<String>(0)
        }
        val sdkDir = macOSSdkPathLocator.find()
        return arrayOf<String>("-isysroot", sdkDir.getAbsolutePath())
    }
}
