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
package org.gradle.internal.resolve

import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.internal.exceptions.Contextual
import org.gradle.util.internal.GUtil

@Contextual
open class ArtifactResolveException : GradleException {
    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(component: ComponentIdentifier, cause: Throwable?) : super(format(component, ""), cause)

    constructor(component: ComponentIdentifier, message: String?) : super(format(component, message))

    constructor(component: ComponentIdentifier, message: String?, cause: Throwable?) : super(format(component, message), cause)

    constructor(artifact: ComponentArtifactIdentifier, cause: Throwable?) : super(format(artifact, ""), cause)

    constructor(artifact: ComponentArtifactIdentifier, message: String?) : super(format(artifact, message))

    constructor(artifact: ComponentArtifactIdentifier, message: String?, cause: Throwable?) : super(format(artifact, message), cause)

    companion object {
        private fun format(artifact: ComponentArtifactIdentifier, message: String?): String {
            val builder = StringBuilder()
            builder.append("Could not download ")
            builder.append(artifact.getDisplayName())
            if (GUtil.isTrue(message)) {
                builder.append(": ")
                builder.append(message)
            }
            return builder.toString()
        }

        private fun format(component: ComponentIdentifier, message: String?): String {
            val builder = StringBuilder()
            builder.append("Could not determine artifacts for ")
            builder.append(component.getDisplayName())
            if (GUtil.isTrue(message)) {
                builder.append(": ")
                builder.append(message)
            }
            return builder.toString()
        }
    }
}
