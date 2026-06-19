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
package org.gradle.internal.logging.console

import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.FlushOutputEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier

/**
 *
 * This listener displays nothing unless it receives periodic [UpdateNowEvent] clock events.
 */
class BuildStatusRenderer(private val listener: OutputEventListener, private val buildStatusLabel: StyledLabel, private val console: Console, private val consoleMetaData: ConsoleMetaData) :
    OutputEventListener {
    private enum class Phase {
        Initializing, Configuring, Executing
    }

    private var buildProgressOperationId: OperationIdentifier? = null
    private var currentPhase: Phase? = null
    private val currentPhaseChildren: MutableSet<OperationIdentifier> = HashSet<OperationIdentifier>()
    private var currentTimePeriod: Long = 0

    // What actually shows up on the console
    private var progressBar: ProgressBar? = null

    // Used to maintain timer
    private var buildStartTimestamp: Long = 0
    private var timerEnabled = false

    override fun onOutput(event: OutputEvent) {
        if (event is ProgressStartEvent) {
            val startEvent = event
            if (startEvent.isBuildOperationStart) {
                if (buildStartTimestamp == 0L && startEvent.parentProgressOperationId == null) {
                    // The very first event starts the Initializing phase
                    // TODO - should use BuildRequestMetaData to determine the build start time
                    buildStartTimestamp = startEvent.timestamp
                    buildProgressOperationId = startEvent.getProgressOperationId()
                    phaseStarted(startEvent, Phase.Initializing)
                } else if (startEvent.buildOperationCategory == BuildOperationCategory.CONFIGURE_ROOT_BUILD) {
                    // Once the root build starts configuring, we are in Configuring phase
                    phaseStarted(startEvent, Phase.Configuring)
                } else if (startEvent.buildOperationCategory == BuildOperationCategory.CONFIGURE_BUILD && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    phaseHasMoreProgress(startEvent)
                } else if (startEvent.buildOperationCategory == BuildOperationCategory.CONFIGURE_PROJECT && currentPhase == Phase.Configuring) {
                    // Any configuring event received from nested or buildSrc builds before the root build starts configuring is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId())
                } else if (startEvent.buildOperationCategory == BuildOperationCategory.RUN_MAIN_TASKS) {
                    phaseStarted(startEvent, Phase.Executing)
                } else if (startEvent.buildOperationCategory == BuildOperationCategory.RUN_WORK && currentPhase == Phase.Executing) {
                    // Any work execution happening in nested or buildSrc builds before the root build has started executing work is ignored
                    phaseHasMoreProgress(startEvent)
                } else if (startEvent.buildOperationCategory.isTopLevelWorkItem() && currentPhase == Phase.Executing) {
                    // Any work execution happening in nested or buildSrc builds before the root build has started executing work is ignored
                    currentPhaseChildren.add(startEvent.getProgressOperationId())
                }
            }
        } else if (event is ProgressCompleteEvent) {
            val completeEvent = event
            if (completeEvent.progressOperationId == buildProgressOperationId) {
                buildEnded()
            } else if (currentPhaseChildren.remove(completeEvent.progressOperationId)) {
                phaseProgressed(completeEvent)
            }
        }

        listener.onOutput(event)

        if (event is UpdateNowEvent) {
            currentTimePeriod = event.timestamp
            renderNow(currentTimePeriod)
        } else if (event is EndOutputEvent || event is FlushOutputEvent) {
            renderNow(currentTimePeriod)
        }
    }

    private fun renderNow(now: Long) {
        if (progressBar != null) {
            buildStatusLabel.setText(progressBar!!.formatProgress(timerEnabled, now - buildStartTimestamp))
        }
        console.flush()
    }

    private fun phaseStarted(progressStartEvent: ProgressStartEvent, phase: Phase) {
        timerEnabled = true
        currentPhase = phase
        currentPhaseChildren.clear()
        val totalProgress = progressStartEvent.totalProgress
        progressBar = ProgressBar.createProgressBar(consoleMetaData, phase.name.uppercase(), totalProgress)
    }

    private fun phaseHasMoreProgress(progressStartEvent: ProgressStartEvent) {
        if (progressBar != null) {
            progressBar!!.moreProgress(progressStartEvent.totalProgress)
        }
    }

    private fun phaseProgressed(progressEvent: ProgressCompleteEvent) {
        if (progressBar != null) {
            progressBar!!.update(progressEvent.isFailed)
        }
    }

    private fun buildEnded() {
        progressBar = ProgressBar.createProgressBar(consoleMetaData, "WAITING", 1)
        currentPhase = null
        buildProgressOperationId = null
        currentPhaseChildren.clear()
        timerEnabled = false
    }
}
