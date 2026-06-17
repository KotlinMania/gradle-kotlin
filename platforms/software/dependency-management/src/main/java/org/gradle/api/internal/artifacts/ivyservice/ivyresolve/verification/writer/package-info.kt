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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import org.gradle.internal.Factory.create
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration.TrustedKey.keyId
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration.TrustCoordinates.matches
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.trustedKeys
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.addTrustedKey
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier.getComponentIdentifier
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.keyringFormat
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys.asciiKeyringsFile
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys.binaryKeyringsFile
import org.gradle.api.logging.Logger.lifecycle
import org.gradle.StartParameter.isOffline
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory.create
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DefaultKeyServers.getOrDefaults
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.keyServers
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.isUseKeyServers
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.isVerifySignatures
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.build
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter.Companion.serialize
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier.configuration
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration.trustedKeys
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration.ignoredKeys
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier.getVerificationMetadata
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService.publicKeyService
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration.keyringFormat
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader.readFromXml
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.addChecksum
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.addIgnoredKey
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService.verify
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.isVerifyMetadata
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.trustedArtifacts
import org.gradle.security.internal.PublicKeyService.findByLongId
import org.gradle.security.internal.PublicKeyService.findByFingerprint
import org.gradle.security.internal.Fingerprint.Companion.fromString
import org.gradle.security.internal.Fingerprint.bytes
import org.gradle.security.internal.PGPUtils.getSize
import org.gradle.security.internal.SecuritySupport.toLongIdHexString
import org.gradle.security.internal.PGPUtils.getUserIDs
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys.effectiveKeyringsFile
import org.gradle.security.internal.SecuritySupport.loadKeyRingFile
import org.gradle.security.internal.Fingerprint.Companion.of
import org.gradle.security.internal.Fingerprint.toString

