/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Decoder.readSmallInt
import org.gradle.internal.serialize.Decoder.readNullableString
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.serialize.Encoder.writeSmallInt
import org.gradle.internal.serialize.Encoder.writeNullableString
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFile
import org.gradle.internal.exceptions.DefaultMultiCauseException.getCauses
import org.gradle.internal.exceptions.DefaultMultiCauseException.getResolutions
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor

