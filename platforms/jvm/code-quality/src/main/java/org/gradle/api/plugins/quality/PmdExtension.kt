/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.resources.TextResource
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.util.Arrays

/**
 * Configuration options for the PMD plugin.
 *
 * @see PmdPlugin
 */
@Suppress("deprecation") // The targetJdk property and TargetJdk type are themselves deprecated.
abstract class PmdExtension(private val project: Project) : CodeQualityExtension() {
    private var targetJdk: TargetJdk? = null
    /**
     * The custom rule set to be used (if any). Replaces `ruleSetFiles`, except that it does not currently support multiple rule sets.
     *
     * See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set.
     *
     * <pre>
     * ruleSetConfig = resources.text.fromFile("config/pmd/myRuleSet.xml")
    </pre> *
     *
     * @since 2.2
     */
    /**
     * The custom rule set to be used (if any). Replaces `ruleSetFiles`, except that it does not currently support multiple rule sets.
     *
     * See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set.
     *
     * <pre>
     * ruleSetConfig = resources.text.fromFile("config/pmd/myRuleSet.xml")
    </pre> *
     *
     * @since 2.2
     */
    @get:ToBeReplacedByLazyProperty
    var ruleSetConfig: TextResource? = null
    private var ruleSetFiles: ConfigurableFileCollection? = null
    /**
     * Whether or not to write PMD results to `System.out`.
     */
    /**
     * Whether or not to write PMD results to `System.out`.
     */
    @get:ToBeReplacedByLazyProperty
    var isConsoleOutput: Boolean = false

    abstract val ruleSetsProperty: ListProperty<String>?

    @get:ToBeReplacedByLazyProperty
    var ruleSets: MutableList<String>
        /**
         * The built-in rule sets to be used. See the [official list](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_rules_java.html) of built-in rule sets.
         *
         * If not configured explicitly, the returned conventional value is "category/java/errorprone.xml", unless [.getRuleSetConfig] returns
         * a non-null value or the return value of [.getRuleSetFiles] is non-empty, in which case the conventional value is an empty list.
         *
         * <pre>
         * ruleSets = ["category/java/errorprone.xml", "category/java/bestpractices.xml"]
        </pre> *
         */
        get() = this.ruleSetsProperty.get()
        /**
         * The built-in rule sets to be used. See the [official list](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_rules_java.html) of built-in rule sets.
         *
         * <pre>
         * ruleSets = ["category/java/errorprone.xml", "category/java/bestpractices.xml"]
        </pre> *
         */
        set(ruleSets) {
            this.ruleSetsProperty.set(ruleSets)
        }

    /**
     * Convenience method for adding rule sets.
     *
     * <pre>
     * ruleSets "category/java/errorprone.xml", "category/java/bestpractices.xml"
    </pre> *
     *
     * @param ruleSets the rule sets to be added
     */
    fun ruleSets(vararg ruleSets: String) {
        this.ruleSetsProperty.addAll(Arrays.asList<String>(*ruleSets))
    }

    /**
     * The target jdk to use with pmd, 1.3, 1.4, 1.5, 1.6, 1.7 or jsp
     *
     */
    @Deprecated(
        """This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets.
          Scheduled to be removed in Gradle 10."""
    )
    fun getTargetJdk(): TargetJdk {
        nagAboutTargetJdkDeprecation("getTargetJdk()")
        return targetJdk!!
    }

    /**
     * Sets the target jdk used with pmd.
     *
     * @param targetJdk The target jdk
     * @since 4.0
     */
    @Deprecated(
        """This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets.
          Scheduled to be removed in Gradle 10."""
    )
    fun setTargetJdk(targetJdk: TargetJdk) {
        nagAboutTargetJdkDeprecation("setTargetJdk(TargetJdk)")
        this.targetJdk = targetJdk
    }

    /**
     * The maximum number of failures to allow before stopping the build.
     *
     * If `ignoreFailures` is set, this is ignored and no limit is enforced.
     *
     * @since 6.4
     */
    abstract val maxFailures: Property<Int>?

    /**
     * Sets the target jdk used with pmd.
     *
     * @param value The value for the target jdk as defined by [TargetJdk.toVersion]
     */
    @Deprecated(
        """This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets.
          Scheduled to be removed in Gradle 10."""
    )
    fun setTargetJdk(value: Any) {
        nagAboutTargetJdkDeprecation("setTargetJdk(Object)")
        targetJdk = whileDisabled<TargetJdk?>(org.gradle.internal.Factory { TargetJdk.toVersion(value) })
    }

    /**
     * The rule priority threshold; violations for rules with a lower priority will not be reported. Default value is 5, which means that all violations will be reported.
     *
     * This is equivalent to PMD's Ant task minimumPriority property.
     *
     * See the official documentation for the [list of priorities](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_configuring_rules.html).
     *
     * <pre>
     * rulesMinimumPriority = 3
    </pre> *
     *
     * @since 6.8
     */
    abstract val rulesMinimumPriority: Property<Int>?

    /**
     * The custom rule set files to be used. See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set file.
     *
     * <pre>
     * ruleSetFiles = files("config/pmd/myRuleSet.xml")
    </pre> *
     */
    @ToBeReplacedByLazyProperty
    fun getRuleSetFiles(): FileCollection {
        return ruleSetFiles!!
    }

    /**
     * The custom rule set files to be used. See the [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_making_rulesets.html) for how to author a rule set file.
     * This adds to the default rule sets defined by [.getRuleSets].
     *
     * <pre>
     * ruleSetFiles = files("config/pmd/myRuleSets.xml")
    </pre> *
     */
    fun setRuleSetFiles(ruleSetFiles: FileCollection) {
        this.ruleSetFiles = project.getObjects().fileCollection().from(ruleSetFiles)
    }

    /**
     * Convenience method for adding rule set files.
     *
     * <pre>
     * ruleSetFiles "config/pmd/myRuleSet.xml"
    </pre> *
     *
     * @param ruleSetFiles the rule set files to be added
     */
    fun ruleSetFiles(vararg ruleSetFiles: Any?) {
        this.ruleSetFiles!!.from(*ruleSetFiles)
    }

    /**
     * Controls whether to use incremental analysis or not.
     *
     * This is only supported for PMD 6.0.0 or better. See [official documentation](https://docs.pmd-code.org/pmd-doc-7.24.0/pmd_userdocs_incremental_analysis.html) for more details.
     *
     * @since 5.6
     */
    abstract val incrementalAnalysis: Property<Boolean>?

    /**
     * The number of threads used by PMD.
     *
     * @since 7.5
     */
    abstract val threads: Property<Int>?

    companion object {
        private fun nagAboutTargetJdkDeprecation(methodWithParams: String) {
            deprecateMethod(PmdExtension::class.java, methodWithParams)
                .withAdvice("This property has no effect for PMD 5.0 and later, which infer the language version from the rule sets. Remove the targetJdk configuration from your build.")!!
                .willBeRemovedInGradle10()
                .withUpgradeGuideSection(9, "deprecated_pmd_target_jdk")!!
                .nagUser()
        }
    }
}
