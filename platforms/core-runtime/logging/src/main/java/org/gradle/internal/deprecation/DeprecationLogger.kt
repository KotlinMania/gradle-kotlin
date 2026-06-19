/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.deprecation

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.problems.Problems
import org.gradle.internal.Factory
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.problems.buildtree.ProblemStream
import java.util.function.Supplier
import javax.annotation.CheckReturnValue
import javax.annotation.concurrent.ThreadSafe

/**
 * Provides entry points for constructing and emitting deprecation messages.
 * The basic deprecation message structure is "Summary. DeprecationTimeline. Context. Advice. Documentation."
 *
 *
 * The deprecateX methods in this class return a builder that guides creation of the deprecation message.
 * Summary is populated by the deprecateX methods in this class.
 * Context can be added in free text using [DeprecationMessageBuilder.withContext].
 * Advice is constructed contextually using [DeprecationMessageBuilder.WithReplacement.replaceWith] methods based on the thing being deprecated.
 * Alternatively, it can be populated using [DeprecationMessageBuilder.withAdvice].
 *
 *
 * DeprecationTimeline is mandatory and is added using one of:
 *
 *  * [DeprecationMessageBuilder.willBeRemovedInGradle10]
 *  * [DeprecationMessageBuilder.willBecomeAnErrorInGradle10]
 *
 *
 *
 * After DeprecationTimeline is set, Documentation reference must be added using one of:
 *
 *  * [DeprecationMessageBuilder.WithDeprecationTimeline.withUpgradeGuideSection]
 *  * [DeprecationMessageBuilder.WithDeprecationTimeline.withDslReference]
 *  * [DeprecationMessageBuilder.WithDeprecationTimeline.withUserManual]
 *
 *
 * In order for the deprecation message to be emitted, terminal operation [DeprecationMessageBuilder.WithDocumentation.nagUser] has to be called after one of the documentation providing methods.
 */
@ThreadSafe
object DeprecationLogger {
    /**
     * Counts the levels of nested `whileDisabled` invocations.
     */
    private val DISABLE_COUNT: ThreadLocal<IntArray> = ThreadLocal.withInitial<IntArray>(Supplier { intArrayOf(0) })

    private val DEPRECATED_FEATURE_HANDLER = LoggingDeprecatedFeatureHandler()

    private var initialized = false

    @JvmStatic
    @Synchronized
    fun init(warningMode: WarningMode, buildOperationProgressEventEmitter: BuildOperationProgressEventEmitter, problemsService: Problems, problemStream: ProblemStream) {
        DEPRECATED_FEATURE_HANDLER.init(warningMode, buildOperationProgressEventEmitter, problemsService, problemStream)
        initialized = true
    }

    @JvmStatic
    @Synchronized
    fun reset() {
        DEPRECATED_FEATURE_HANDLER.reset()
    }

    @JvmStatic
    @Synchronized
    fun reportSuppressedDeprecations() {
        DEPRECATED_FEATURE_HANDLER.reportSuppressedDeprecations()
    }

    @JvmStatic
    val deprecationFailure: Throwable?
        get() = DEPRECATED_FEATURE_HANDLER.deprecationFailure

    /**
     * This is a rather generic deprecation entry point - consider using a more specific deprecation method and only resort to this one if there is no fit.
     *
     * Output: ${feature} has been deprecated.
     */
    @JvmStatic
    @CheckReturnValue
    fun deprecate(feature: String): DeprecationMessageBuilder<*> {
        return ExplicitDeprecationMessageBuilder(feature)
    }

    /**
     * Indirect usage means that stack trace at the time the deprecation is logged does not indicate the call site.
     * This directs GE to not display unhelpful stacktraces with the deprecation.
     *
     *
     * Output: ${feature} has been deprecated.
     */
    @JvmStatic
    @CheckReturnValue
    fun deprecateIndirectUsage(feature: String): DeprecationMessageBuilder<*> {
        val builder = deprecate(feature)
        builder.setIndirectUsage()
        return builder
    }

    /**
     * Output: ${feature} has been deprecated.
     */
    @CheckReturnValue
    fun deprecateBuildInvocationFeature(feature: String): DeprecationMessageBuilder<*> {
        val builder = deprecate(feature)
        builder.setBuildInvocationUsage()
        return builder
    }

    /**
     * Output: ${behaviour}. This behavior is deprecated.
     */
    @JvmStatic
    @CheckReturnValue
    fun deprecateBehaviour(behaviour: String): DeprecationMessageBuilder.DeprecateBehaviour {
        return DeprecationMessageBuilder.DeprecateBehaviour(behaviour)
    }

    /**
     * Output: ${action} has been deprecated.
     */
    @JvmStatic
    @CheckReturnValue
    fun deprecateAction(action: String): DeprecationMessageBuilder.DeprecateAction {
        return DeprecationMessageBuilder.DeprecateAction(action)
    }

    /**
     * Output: The ${property} property has been deprecated.
     */
    @CheckReturnValue
    fun deprecateProperty(propertyClass: Class<*>, property: String): DeprecationMessageBuilder.DeprecateProperty {
        return DeprecationMessageBuilder.DeprecateProperty(propertyClass, property)
    }

    /**
     * Output: The ${property} system property has been deprecated.
     */
    @CheckReturnValue
    fun deprecateSystemProperty(systemProperty: String): DeprecationMessageBuilder.DeprecateSystemProperty {
        return DeprecationMessageBuilder.DeprecateSystemProperty(systemProperty)
    }

    /**
     * Output: The ${parameter} named parameter has been deprecated.
     */
    @CheckReturnValue
    fun deprecateNamedParameter(parameter: String): DeprecationMessageBuilder.DeprecateNamedParameter {
        return DeprecationMessageBuilder.DeprecateNamedParameter(parameter)
    }

    /**
     * Output: The ${method} method has been deprecated.
     */
    @CheckReturnValue
    @JvmStatic
    fun deprecateMethod(methodClass: Class<*>, methodWithParams: String): DeprecationMessageBuilder.DeprecateMethod {
        return DeprecationMessageBuilder.DeprecateMethod(methodClass, methodWithParams)
    }

    /**
     * Output: Using method ${invocation} has been deprecated.
     */
    @CheckReturnValue
    fun deprecateInvocation(methodWithParams: String): DeprecationMessageBuilder.DeprecateInvocation {
        return DeprecationMessageBuilder.DeprecateInvocation(methodWithParams)
    }

    @CheckReturnValue
    fun deprecateType(type: Class<*>): DeprecationMessageBuilder.DeprecateType {
        return DeprecationMessageBuilder.DeprecateType(type.getCanonicalName())
    }

    /**
     * Output: The ${task} task has been deprecated.
     */
    @JvmStatic
    @CheckReturnValue
    fun deprecateTask(task: String): DeprecationMessageBuilder.DeprecateTask {
        return DeprecationMessageBuilder.DeprecateTask(task)
    }

    /**
     * Output: The task type ${task.getCanonicalName()} (used by the ${task.getPath()} task) has been deprecated.
     */
    @CheckReturnValue
    fun deprecateTaskType(task: Class<*>, path: String): DeprecationMessageBuilder.DeprecateTaskType {
        return DeprecationMessageBuilder.DeprecateTaskType(task.getCanonicalName(), path)
    }

    /**
     * Output: The ${plugin} plugin has been deprecated.
     */
    @CheckReturnValue
    fun deprecatePlugin(plugin: String): DeprecationMessageBuilder.DeprecatePlugin {
        return DeprecationMessageBuilder.DeprecatePlugin(plugin)
    }

    /**
     * Output: Internal API ${api} has been deprecated.
     */
    @CheckReturnValue
    fun deprecateInternalApi(api: String): DeprecationMessageBuilder.DeprecateInternalApi {
        return DeprecationMessageBuilder.DeprecateInternalApi(api)
    }

    /**
     * Output: The ${configurationType} configuration has been deprecated for ${declarationType}.
     */
    @JvmStatic
    @CheckReturnValue
    fun deprecateConfiguration(configurationType: String): DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector {
        return DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector(configurationType)
    }

    fun nagUserWith(deprecationMessageBuilder: DeprecationMessageBuilder<*>, calledFrom: Class<*>) {
        if (isEnabled) {
            // ideally, it should be checked outside of this condition, but that would fail a lot of unit tests that use suppressed deprecations
            check(initialized) {
                "DeprecationLogger has not been initialized. " +
                        "Most probably, it's because you are trying to use it from the launcher/wrapper. " +
                        "It's not available there. Move the deprecation logging to the daemon or use LOGGER.warn() instead. " +
                        "Another reason could be that you are trying to use it from a unit test. " +
                        "In that case, either fix the test to not use deprecated features, or mark it with @ExpectDeprecation. " +
                        "If you hit this error as a user of Gradle, please report it as a bug. " +
                        "The original deprecation message was: " +
                        deprecationMessageBuilder.build().toDeprecatedFeatureUsage(calledFrom).formattedMessage()
            }
            val deprecationMessage = deprecationMessageBuilder.build()
            val featureUsage = deprecationMessage.toDeprecatedFeatureUsage(calledFrom)
            nagUserWith(featureUsage)

            if (!featureUsage.formattedMessage().contains("deprecated")) {
                throw RuntimeException("Deprecation message does not contain the word 'deprecated'. Message: \n" + featureUsage.formattedMessage())
            }
        }
    }

    @JvmStatic
    fun <T : Any?> whileDisabled(factory: Factory<T?>): T? {
        disable()
        try {
            return factory.create()
        } finally {
            maybeEnable()
        }
    }

    @JvmStatic
    fun whileDisabled(action: Runnable) {
        disable()
        try {
            action.run()
        } finally {
            maybeEnable()
        }
    }

    fun <T, E : Exception> whileDisabledThrowing(factory: ThrowingFactory<T?, E>): T? {
        disable()
        try {
            return toUncheckedThrowingFactory<T?, E>(factory).create()
        } finally {
            maybeEnable()
        }
    }

    fun <E : Exception> whileDisabledThrowing(runnable: ThrowingRunnable<E>) {
        disable()
        try {
            toUncheckedThrowingRunnable<E>(runnable).run()
        } finally {
            maybeEnable()
        }
    }

    private fun disable() {
        DISABLE_COUNT.get()[0]++
    }

    private fun maybeEnable() {
        DISABLE_COUNT.get()[0]--
    }

    private val isEnabled: Boolean
        get() =// log deprecation messages only after the outermost whileDisabled finished execution
            DISABLE_COUNT.get()[0] == 0

    /**
     * Turns a [ThrowingFactory] into a [Factory].
     * The compiler is happy with the casting that allows to hide the checked exception.
     * The runtime is happy with the casting because the checked exception type information is captured in a generic type parameter which gets erased.
     */
    private fun <T, E : Exception> toUncheckedThrowingFactory(throwingFactory: ThrowingFactory<T?, E>): Factory<T?> {
        return object : Factory<T?> {
            override fun create(): T? {
                val factory = throwingFactory as ThrowingFactory<T?, RuntimeException>
                return factory.create()
            }
        }
    }

    /**
     * Turns a [ThrowingRunnable] into a [Runnable].
     *
     * @see .toUncheckedThrowingFactory
     */
    private fun <E : Exception> toUncheckedThrowingRunnable(throwingRunnable: ThrowingRunnable<E>): Runnable {
        return object : Runnable {
            override fun run() {
                val runnable = throwingRunnable as ThrowingRunnable<RuntimeException>
                runnable.run()
            }
        }
    }

    @Synchronized
    private fun nagUserWith(usage: DeprecatedFeatureUsage) {
        DEPRECATED_FEATURE_HANDLER.featureUsed(usage)
    }

    interface ThrowingFactory<T, E : Exception> {
        fun create(): T?
    }

    interface ThrowingRunnable<E : Exception> {
        fun run()
    }

    private class ExplicitDeprecationMessageBuilder(private val feature: String) : DeprecationMessageBuilder<ExplicitDeprecationMessageBuilder>() {
        init {
            setSummary(feature + " has been deprecated.")
        }

        override fun build(): DeprecationMessage {
            if (problemIdValue == null) {
                setProblemId(DeprecationMessageBuilder.Companion.createDefaultDeprecationId(feature))
            }
            return super.build()
        }
    }
}
