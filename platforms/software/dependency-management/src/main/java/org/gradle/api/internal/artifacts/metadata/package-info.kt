/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.artifacts.metadata

import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier.getComponentIdentifier
import org.gradle.internal.component.model.ComponentArtifactMetadata.getComponentId
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.serialize.AbstractSerializer.equals
import org.gradle.internal.serialize.AbstractSerializer.hashCode
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier.getComponentIdentifier
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier.fileName
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier.getComponentIdentifier
import org.gradle.internal.serialize.Decoder.readNullableString
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata.getComponentIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata.publishArtifact
import org.gradle.internal.serialize.Encoder.writeNullableString

