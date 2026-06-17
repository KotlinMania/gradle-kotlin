/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.tooling.internal.provider.continuous

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.internal.watch.vfs.FileChangeListener
import java.nio.file.Files
import java.nio.file.Path

class FileEventCollector(private val buildInputs: BuildInputHierarchy, private val onRelevantChangeAction: Runnable) : FileChangeListener {
    private val aggregatedEvents: MutableMap<Path, FileWatcherRegistry.Type> = LinkedHashMap<Path, FileWatcherRegistry.Type>()
    private var moreChangesCount = 0
    private var errorWhenWatching = false

    override fun handleChange(type: FileWatcherRegistry.Type, path: Path) {
        val absolutePath = path.toString()
        if (buildInputs.isInput(absolutePath)) {
            // got a change, store it
            onChangeToInputs(type, path)
            onRelevantChangeAction.run()
        }
    }

    override fun stopWatchingAfterError() {
        errorWhenWatching = true
        onRelevantChangeAction.run()
    }

    fun onChangeToInputs(type: FileWatcherRegistry.Type, path: Path) {
        val existingEvent = aggregatedEvents.get(path)
        if (existingEvent == type ||
            (existingEvent == FileWatcherRegistry.Type.CREATED && type == FileWatcherRegistry.Type.MODIFIED)
        ) {
            return
        }

        if (existingEvent != null || aggregatedEvents.size < SHOW_INDIVIDUAL_CHANGES_LIMIT) {
            aggregatedEvents.put(path, type)
        } else {
            moreChangesCount++
        }
    }

    fun reportChanges(logger: StyledTextOutput) {
        for (entry in aggregatedEvents.entries) {
            val type = entry.value
            val path = entry.key
            showIndividualChange(logger, path, type)
        }
        if (moreChangesCount > 0) {
            logOutput(logger, "and some more changes")
        }
        if (errorWhenWatching) {
            logOutput(logger, "Error when watching files - triggering a rebuild")
        }
    }

    private fun showIndividualChange(logger: StyledTextOutput, path: Path, changeType: FileWatcherRegistry.Type) {
        val changeDescription: String?
        when (changeType) {
            FileWatcherRegistry.Type.CREATED -> changeDescription = "new " + (if (Files.isDirectory(path)) "directory" else "file")
            FileWatcherRegistry.Type.REMOVED -> changeDescription = "deleted"
            FileWatcherRegistry.Type.MODIFIED -> changeDescription = "modified"
            else -> changeDescription = "modified"
        }
        logOutput(logger, "%s: %s", changeDescription, path.toString())
    }

    private fun logOutput(logger: StyledTextOutput, message: String, vararg objects: Any) {
        logger.formatln(message, *objects)
    }

    companion object {
        const val SHOW_INDIVIDUAL_CHANGES_LIMIT: Int = 3
    }
}
