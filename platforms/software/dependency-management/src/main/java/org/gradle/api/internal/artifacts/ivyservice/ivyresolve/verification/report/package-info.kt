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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report

import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory.create
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure.filePath
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure.signatureFile
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.errors
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.internal.DocumentationRegistry.getDocumentationFor
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier.getComponentIdentifier
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure.kind
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure.expected
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure.actual
import org.gradle.api.internal.artifacts.verification.verifier.InvalidSignatureFile.causeDescription
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.SignatureError.publicKey
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.appendKeyDetails
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure.SignatureError.kind
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.blankLine
import org.gradle.internal.logging.text.TreeFormatter.append
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure.explainTo

