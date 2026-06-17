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
package org.gradle.internal.resource.transfer

import org.gradle.internal.file.FileAccessTracker.markAccessed
import org.gradle.internal.serialize.Decoder.readBoolean
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Decoder.readLong
import org.gradle.internal.serialize.Decoder.readNullableString
import org.gradle.internal.serialize.Decoder.readSmallLong
import org.gradle.internal.serialize.Encoder.writeBoolean
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.serialize.Encoder.writeLong
import org.gradle.internal.serialize.Encoder.writeNullableString
import org.gradle.internal.serialize.Encoder.writeSmallLong
import org.gradle.internal.logging.progress.ResourceOperation.setContentLength
import org.gradle.internal.logging.progress.ResourceOperation.totalProcessedBytes
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.reflect.JavaMethod.parameterTypes
import org.gradle.internal.reflect.JavaMethod.invoke
import org.gradle.util.internal.CollectionUtils.findFirst
import org.gradle.api.specs.Spec.isSatisfiedBy

