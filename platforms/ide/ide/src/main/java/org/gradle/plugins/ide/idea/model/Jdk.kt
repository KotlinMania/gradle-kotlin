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
package org.gradle.plugins.ide.idea.model

import com.google.common.base.Objects

/**
 * Represents information for the project Java SDK.
 * This translates to attributes of the ProjectRootManager element in the ipr.
 */
class Jdk {
    var isAssertKeyword: Boolean = false
    var isJdk15: Boolean = false
    var languageLevel: String?
    var projectJdkName: String?

    constructor(jdkName: String, ideaLanguageLevel: IdeaLanguageLevel) {
        if (jdkName.startsWith("1.4")) {
            this.isAssertKeyword = true
            this.isJdk15 = false
        } else if (jdkName.compareTo("1.5") >= 0) {
            this.isAssertKeyword = true
            this.isJdk15 = true
        } else {
            this.isAssertKeyword = false
        }
        languageLevel = ideaLanguageLevel.getLevel()
        projectJdkName = jdkName
    }

    constructor(assertKeyword: Boolean, jdk15: Boolean, languageLevel: String?, projectJdkName: String?) {
        this.isAssertKeyword = assertKeyword
        this.isJdk15 = jdk15
        this.languageLevel = languageLevel
        this.projectJdkName = projectJdkName
    }

    override fun toString(): String {
        return ("Jdk{"
                + "assertKeyword=" + this.isAssertKeyword
                + ", jdk15=" + this.isJdk15
                + ", languageLevel='" + languageLevel
                + "', projectJdkName='" + projectJdkName
                + "\'" + "}")
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (javaClass != o.javaClass) {
            return false
        }
        val jdk = o as Jdk
        if (this.isAssertKeyword != jdk.isAssertKeyword) {
            return false
        }
        if (this.isJdk15 != jdk.isJdk15) {
            return false
        }
        return Objects.equal(languageLevel, jdk.languageLevel)
                && Objects.equal(projectJdkName, jdk.projectJdkName)
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + (if (this.isAssertKeyword) 1 else 0)
        result = 31 * result + (if (this.isJdk15) 1 else 0)
        result = 31 * result + (if (languageLevel != null) languageLevel.hashCode() else 0)
        result = 31 * result + (if (projectJdkName != null) projectJdkName.hashCode() else 0)
        return result
    }
}
