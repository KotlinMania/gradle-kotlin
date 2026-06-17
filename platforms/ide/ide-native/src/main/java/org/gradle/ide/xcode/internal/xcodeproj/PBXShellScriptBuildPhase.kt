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
package org.gradle.ide.xcode.internal.xcodeproj

import com.dd.plist.NSArray
import com.dd.plist.NSString

/**
 * Build phase which represents running a shell script.
 */
class PBXShellScriptBuildPhase : PBXBuildPhase() {
    /**
     * Returns the list (possibly empty) of files passed as input to the shell script.
     * May not be actual paths, because they can have variable interpolations.
     */
    val inputPaths: MutableList<String?>

    /**
     * Returns the list (possibly empty) of files created as output of the shell script.
     * May not be actual paths, because they can have variable interpolations.
     */
    val outputPaths: MutableList<String?>
    /**
     * Returns the path to the shell under which the script is to be executed.
     * Defaults to "/bin/sh".
     */
    /**
     * Sets the path to the shell under which the script is to be executed.
     */
    var shellPath: String? = null
    /**
     * Gets the contents of the shell script to execute under the shell
     * returned by [.getShellPath].
     */
    /**
     * Sets the contents of the script to execute.
     */
    var shellScript: String? = null

    init {
        this.inputPaths = ArrayList<String?>()
        this.outputPaths = ArrayList<String?>()
    }

    override fun isa(): String {
        return "PBXShellScriptBuildPhase"
    }

    override fun serializeInto(s: XcodeprojSerializer) {
        super.serializeInto(s)

        val inputPathsArray = NSArray(inputPaths.size)
        for (i in inputPaths.indices) {
            inputPathsArray.setValue(i, NSString(inputPaths.get(i)))
        }
        s.addField("inputPaths", inputPathsArray)

        val outputPathsArray = NSArray(outputPaths.size)
        for (i in outputPaths.indices) {
            outputPathsArray.setValue(i, NSString(outputPaths.get(i)))
        }
        s.addField("outputPaths", outputPathsArray)

        val shellPathString: NSString?
        if (shellPath == null) {
            shellPathString = DEFAULT_SHELL_PATH
        } else {
            shellPathString = NSString(shellPath)
        }
        s.addField("shellPath", shellPathString)

        val shellScriptString: NSString?
        if (shellScript == null) {
            shellScriptString = DEFAULT_SHELL_SCRIPT
        } else {
            shellScriptString = NSString(shellScript)
        }
        s.addField("shellScript", shellScriptString)
    }

    companion object {
        private val DEFAULT_SHELL_PATH = NSString("/bin/sh")
        private val DEFAULT_SHELL_SCRIPT = NSString("")
    }
}
