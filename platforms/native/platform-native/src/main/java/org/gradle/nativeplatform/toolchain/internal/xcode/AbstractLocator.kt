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
package org.gradle.nativeplatform.toolchain.internal.xcode

import org.gradle.process.internal.ExecActionFactory
import java.io.ByteArrayOutputStream
import java.io.File

abstract class AbstractLocator protected constructor(private val execActionFactory: ExecActionFactory) {
    private var cachedLocation: File? = null

    protected abstract val xcrunFlags: MutableList<String?>?

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    fun find(): File {
        synchronized(this) {
            if (cachedLocation == null) {
                val outputStream = ByteArrayOutputStream()
                val execAction = execActionFactory.newExecAction()
                execAction!!.executable("xcrun")
                execAction.workingDir(System.getProperty("user.dir"))
                execAction.args(this.xcrunFlags)

                val developerDir = System.getenv("DEVELOPER_DIR")
                if (developerDir != null) {
                    execAction.environment("DEVELOPER_DIR", developerDir)
                }
                execAction.setStandardOutput(outputStream)
                execAction.execute()!!.assertNormalExitValue()

                cachedLocation = File(outputStream.toString().replace("\n", ""))
            }
            return cachedLocation!!
        }
    }
}
