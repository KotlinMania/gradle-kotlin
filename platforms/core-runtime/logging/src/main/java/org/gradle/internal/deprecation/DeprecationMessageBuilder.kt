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

import com.google.common.base.Joiner
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DefaultGradleVersion
import javax.annotation.CheckReturnValue

@CheckReturnValue
open class DeprecationMessageBuilder<T : DeprecationMessageBuilder<T>> {
    protected var summaryValue: String? = null
    private var deprecationTimeline: DeprecationTimeline? = null
    private var context: String? = null
    private var advice: String? = null
    private var documentation: DocLink? = null
    private var usageType = DeprecatedFeatureUsage.Type.USER_CODE_DIRECT

    protected var problemIdDisplayNameValue: String? = null
    protected var problemIdValue: String? = null

    protected open fun createDefaultDeprecationIdDisplayName(): String? {
        return summaryValue
    }

    fun withContext(context: String): T {
        this.context = context
        return this as T
    }

    fun withAdvice(advice: String): T {
        this.advice = advice
        return this as T
    }

    fun withProblemIdDisplayName(problemIdDisplayNameValue: String): T {
        this.problemIdDisplayNameValue = problemIdDisplayNameValue
        return this as T
    }

    fun withProblemId(problemIdValue: String): T {
        this.problemIdValue = problemIdValue
        return this as T
    }

    /**
     * Output: This is scheduled to be removed in Gradle 10.
     */
    open fun willBeRemovedInGradle10(): WithDeprecationTimeline {
        this.deprecationTimeline = DeprecationTimeline.Companion.willBeRemovedInVersion(GRADLE10)
        return WithDeprecationTimeline(this)
    }

    /**
     * Output: This will fail with an error in Gradle 10.
     */
    fun willBecomeAnErrorInGradle10(): WithDeprecationTimeline {
        this.deprecationTimeline = DeprecationTimeline.Companion.willBecomeAnErrorInVersion(GRADLE10)
        return WithDeprecationTimeline(this)
    }

    /**
     * Output: This will fail with an error in Gradle 11.
     */
    fun willBecomeAnErrorInGradle11(): WithDeprecationTimeline {
        this.deprecationTimeline = DeprecationTimeline.Companion.willBecomeAnErrorInVersion(GRADLE11)
        return WithDeprecationTimeline(this)
    }

    /**
     * Output: This will fail with an error in Gradle X.
     *
     *
     * Where X is the current major Gradle version + 1.
     *
     * NOTE: This should be used sparingly. It is better to use the version-specific methods for deprecations that will become errors.
     * This is intended for persistent deprecations that will never be removed.
     * As an example, Gradle will always have a deprecation about using a version of Java older than the future minimum version.
     */
    fun willBecomeAnErrorInNextMajorGradleVersion(): WithDeprecationTimeline {
        val nextMajor: GradleVersion = DefaultGradleVersion.current().getNextMajorVersion()
        this.deprecationTimeline = DeprecationTimeline.Companion.willBecomeAnErrorInVersion(nextMajor)
        return WithDeprecationTimeline(this)
    }

    /**
     * Output: Starting with Gradle 10, ${message}.
     */
    fun startingWithGradle10(message: String): WithDeprecationTimeline {
        this.deprecationTimeline = DeprecationTimeline.Companion.startingWithVersion(GRADLE10, message)
        return WithDeprecationTimeline(this)
    }

    /**
     * Output: Starting with Gradle 11, ${message}.
     */
    fun startingWithGradle11(message: String): WithDeprecationTimeline {
        this.deprecationTimeline = DeprecationTimeline.Companion.startingWithVersion(GRADLE11, message)
        return WithDeprecationTimeline(this)
    }

    fun setIndirectUsage() {
        this.usageType = DeprecatedFeatureUsage.Type.USER_CODE_INDIRECT
    }

    fun setBuildInvocationUsage() {
        this.usageType = DeprecatedFeatureUsage.Type.BUILD_INVOCATION
    }

    fun setSummary(summaryValue: String?) {
        this.summaryValue = summaryValue
    }

    fun setAdvice(advice: String) {
        this.advice = advice
    }

    fun setDocumentation(documentation: DocLink?) {
        this.documentation = documentation
    }

    fun setProblemIdDisplayName(problemIdDisplayNameValue: String?) {
        this.problemIdDisplayNameValue = problemIdDisplayNameValue
    }

    fun setDeprecationTimeline(deprecationTimeline: DeprecationTimeline) {
        this.deprecationTimeline = deprecationTimeline
    }

    open fun build(): DeprecationMessage {
        if (problemIdDisplayNameValue == null) {
            setProblemIdDisplayName(createDefaultDeprecationIdDisplayName())
        }

        if (problemIdValue == null) {
            setProblemId(Companion.createDefaultDeprecationId(createDefaultDeprecationIdDisplayName()!!))
        }

        return DeprecationMessage(summaryValue!!, deprecationTimeline.toString(), advice, context, documentation, usageType, problemIdDisplayNameValue!!, problemIdValue!!)
    }

    fun setProblemId(problemIdValue: String) {
        this.problemIdValue = problemIdValue
    }

    open class WithDeprecationTimeline(private val builder: DeprecationMessageBuilder<*>) : Documentation.AbstractBuilder<WithDocumentation>() {
        override fun withDocumentation(documentation: DocLink?): WithDocumentation {
            builder.setDocumentation(documentation)
            return WithDocumentation(builder)
        }
    }

    class WithDocumentation internal constructor(private val builder: DeprecationMessageBuilder<*>) {
        /**
         * Terminal operation. Emits the deprecation message.
         */
        fun nagUser() {
            DeprecationLogger.nagUserWith(builder, WithDocumentation::class.java)
        }
    }

    abstract class WithReplacement<T, SELF : WithReplacement<T, SELF>> internal constructor(protected val subject: String) : DeprecationMessageBuilder<SELF>() {
        private var replacement: T? = null

        /**
         * Constructs advice message based on the context.
         *
         * deprecateProperty: Please use the ${replacement} property instead.
         * deprecateMethod/deprecateInvocation: Please use the ${replacement} method instead.
         * deprecatePlugin: Please use the ${replacement} plugin instead.
         * deprecateTask: Please use the ${replacement} task instead.
         * deprecateInternalApi: Please use ${replacement} instead.
         * deprecateNamedParameter: Please use the ${replacement} named parameter instead.
         */
        fun replaceWith(replacement: T): SELF {
            this.replacement = replacement
            return this as SELF
        }

        open fun formatSubject(): String {
            return subject
        }

        abstract fun formatSummary(subject: String): String?

        abstract fun formatAdvice(replacement: T): String?


        override fun build(): DeprecationMessage {
            setSummary(formatSummary(formatSubject()))
            val replacement = this.replacement
            if (replacement != null) {
                setAdvice(formatAdvice(replacement)!!)
            }

            if (problemIdDisplayNameValue == null) {
                setProblemIdDisplayName(summaryValue)
            }
            if (problemIdValue == null) {
                setProblemId(Companion.createDefaultDeprecationId(createDefaultDeprecationIdDisplayName()!!))
            }

            return super.build()
        }
    }

    class DeprecateAction internal constructor(subject: String) : WithReplacement<String, DeprecateAction>(subject) {
        override fun createDefaultDeprecationIdDisplayName(): String {
            return subject
        }

        override fun formatSummary(subject: String): String {
            return String.format("%s has been deprecated.", subject)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use %s instead.", replacement)
        }
    }

    class DeprecateNamedParameter internal constructor(parameter: String) : WithReplacement<String, DeprecateNamedParameter>(parameter) {
        override fun formatSummary(parameter: String): String {
            return String.format("The %s named parameter has been deprecated.", parameter)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use the %s named parameter instead.", replacement)
        }
    }

    class DeprecateProperty internal constructor(private val propertyClass: Class<*>, private val property: String) : WithReplacement<String, DeprecateProperty>(
        property
    ) {
        /**
         * DO NOT CALL THIS METHOD
         */
        @Deprecated("")
        fun willBeRemovedInGradle9(): WithDeprecationTimeline {
            setDeprecationTimeline(DeprecationTimeline.Companion.willBeRemovedInVersion(GRADLE10))
            return WithDeprecationTimeline(this)
        }

        override fun willBeRemovedInGradle10(): WithDeprecationTimeline {
            setDeprecationTimeline(DeprecationTimeline.Companion.willBeRemovedInVersion(GRADLE10))
            return WithDeprecationTimeline(this)
        }

        inner class WithDeprecationTimeline(private val builder: DeprecateProperty) : DeprecationMessageBuilder.WithDeprecationTimeline(
            builder
        ) {
            /**
             * Output: See DSL_REFERENCE_URL for more details.
             */
            @CheckReturnValue
            fun withDslReference(): WithDocumentation {
                setDocumentation(Documentation.Companion.dslReference(propertyClass, property))
                return WithDocumentation(builder)
            }
        }

        override fun formatSubject(): String {
            return String.format("%s.%s", propertyClass.getSimpleName(), property)
        }

        override fun formatSummary(property: String): String {
            return String.format("The %s property has been deprecated.", property)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use the %s property instead.", replacement)
        }
    }

    class DeprecateSystemProperty internal constructor(private val systemProperty: String) : WithReplacement<String, DeprecateSystemProperty>(
        systemProperty
    ) {
        init {
            // This never happens in user code
            setIndirectUsage()
        }

        override fun formatSubject(): String {
            return systemProperty
        }

        override fun formatSummary(property: String): String {
            return String.format("The %s system property has been deprecated.", property)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use the %s system property instead.", replacement)
        }
    }

    @CheckReturnValue
    class ConfigurationDeprecationTypeSelector internal constructor(private val configuration: String) {
        fun forArtifactDeclaration(): DeprecateConfiguration {
            return DeprecateConfiguration(configuration, ConfigurationDeprecationType.ARTIFACT_DECLARATION)
        }

        fun forConsumption(): DeprecateConfiguration {
            return DeprecateConfiguration(configuration, ConfigurationDeprecationType.CONSUMPTION)
        }

        fun forDependencyDeclaration(): DeprecateConfiguration {
            return DeprecateConfiguration(configuration, ConfigurationDeprecationType.DEPENDENCY_DECLARATION)
        }

        fun forResolution(): DeprecateConfiguration {
            return DeprecateConfiguration(configuration, ConfigurationDeprecationType.RESOLUTION)
        }
    }

    class DeprecateConfiguration internal constructor(configuration: String, private val deprecationType: ConfigurationDeprecationType) :
        WithReplacement<MutableList<String>, DeprecateConfiguration>(configuration) {
        init {
            if (!deprecationType.inUserCode) {
                setIndirectUsage()
            }
        }

        override fun formatSummary(configuration: String): String {
            return String.format("The %s configuration has been deprecated for %s.", configuration, deprecationType.displayName())
        }

        override fun formatAdvice(replacements: MutableList<String>): String {
            if (replacements.isEmpty()) {
                return "Please " + deprecationType.usage + " another configuration instead."
            }
            return String.format("Please %s the %s configuration instead.", deprecationType.usage, Joiner.on(" or ").join(replacements))
        }
    }

    class DeprecateMethod internal constructor(private val methodClass: Class<*>, private val methodWithParams: String) : WithReplacement<String, DeprecateMethod>(
        methodWithParams
    ) {
        override fun formatSubject(): String {
            return String.format("%s.%s", methodClass.getSimpleName(), methodWithParams)
        }

        override fun formatSummary(method: String): String {
            return String.format("The %s method has been deprecated.", method)
        }

        override fun formatAdvice(replacement: String): String {
            return pleaseUseThisMethodInstead(replacement)
        }

        companion object {
            fun pleaseUseThisMethodInstead(replacement: String): String {
                return String.format("Please use the %s method instead.", replacement)
            }
        }
    }

    class DeprecateInvocation internal constructor(invocation: String) : WithReplacement<String, DeprecateInvocation>(invocation) {
        override fun formatSummary(invocation: String): String {
            return String.format("Using method %s has been deprecated.", invocation)
        }

        override fun formatAdvice(replacement: String): String {
            return DeprecateMethod.Companion.pleaseUseThisMethodInstead(replacement)
        }
    }

    class DeprecateType internal constructor(type: String) : WithReplacement<String, DeprecateType>(type) {
        override fun formatSummary(type: String): String {
            return String.format("The %s type has been deprecated.", type)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use the %s type instead.", replacement)
        }
    }

    class DeprecateTask internal constructor(task: String) : WithReplacement<String, DeprecateTask>(task) {
        override fun formatSummary(task: String): String {
            return String.format("The %s task has been deprecated.", task)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use the %s task instead.", replacement)
        }
    }

    class DeprecateTaskType internal constructor(task: String, private val path: String) : WithReplacement<Class<*>, DeprecateTaskType>(task) {
        override fun formatSummary(type: String): String {
            return String.format("The task type %s (used by the %s task) has been deprecated.", type, path)
        }

        override fun formatAdvice(replacement: Class<*>): String {
            return String.format("Please use the %s type instead.", replacement.getCanonicalName())
        }
    }

    class DeprecatePlugin internal constructor(plugin: String) : WithReplacement<String, DeprecatePlugin>(plugin) {
        private var externalReplacement = false

        override fun formatSummary(plugin: String): String {
            return String.format("The %s plugin has been deprecated.", plugin)
        }

        override fun formatAdvice(replacement: String): String {
            return if (externalReplacement) String.format("Consider using the %s plugin instead.", replacement) else String.format("Please use the %s plugin instead.", replacement)
        }

        /**
         * Advice output: Consider using the ${replacement} plugin instead.
         */
        fun replaceWithExternalPlugin(replacement: String): DeprecatePlugin {
            this.externalReplacement = true
            return replaceWith(replacement)
        }
    }

    class DeprecateInternalApi internal constructor(api: String) : WithReplacement<String, DeprecateInternalApi>(api) {
        override fun formatSummary(api: String): String {
            return String.format("Internal API %s has been deprecated.", api)
        }

        override fun formatAdvice(replacement: String): String {
            return String.format("Please use %s instead.", replacement)
        }
    }

    class DeprecateBehaviour(private val behaviour: String) : DeprecationMessageBuilder<DeprecateBehaviour>() {
        override fun build(): DeprecationMessage {
            setSummary(String.format("%s This behavior has been deprecated.", behaviour))
            return super.build()
        }
    }

    companion object {
        private val GRADLE10: GradleVersion = GradleVersion.version("10.0.0")
        private val GRADLE11: GradleVersion = GradleVersion.version("11.0.0")

        @JvmStatic
        fun withDocumentation(warning: ProblemInternal, withDeprecationTimeline: WithDeprecationTimeline): WithDocumentation {
            val docLink = warning.getDefinition()!!.getDocumentationLink()
            if (docLink != null) {
                return withDeprecationTimeline
                    .withDocumentation(docLink)
            }
            return withDeprecationTimeline.undocumented()
        }

        const val DASH: Char = '-'

        @JvmStatic
        fun createDefaultDeprecationId(vararg ids: String): String {
            val sb = StringBuilder()
            for (id in ids) {
                val cleanId: CharSequence = createDashedId(id)
                if (cleanId.length > 0) {
                    sb.append(cleanId)
                    sb.append(DASH)
                }
            }
            removeTrailingDashes(sb)
            return sb.toString()
        }

        private fun removeTrailingDashes(sb: StringBuilder) {
            while (sb.length > 0 && sb.get(sb.length - 1) == DASH) {
                sb.setLength(sb.length - 1)
            }
        }

        private fun createDashedId(id: String): CharSequence {
            val cleanId = StringBuilder()
            var previousWasDash = false
            for (i in 0..<id.length) {
                val c = id.get(i)
                if (Character.isLetter(c)) {
                    previousWasDash = false
                    cleanId.append(c.lowercaseChar())
                } else {
                    if (previousWasDash) {
                        continue
                    }
                    cleanId.append(DASH)
                    previousWasDash = true
                }
            }
            return cleanId
        }
    }
}
