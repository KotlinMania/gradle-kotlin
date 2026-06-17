/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.resource.transport.aws.s3

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PartETag
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.UploadPartRequest
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import org.gradle.api.credentials.AwsCredentials
import org.gradle.internal.resource.ResourceExceptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import kotlin.math.min

@Suppress("deprecation")
class S3Client {
    private val resourceResolver = S3ResourceResolver()
    private val amazonS3Client: AmazonS3Client
    private val s3ConnectionProperties: S3ConnectionProperties

    constructor(amazonS3Client: AmazonS3Client, s3ConnectionProperties: S3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties
        this.amazonS3Client = amazonS3Client
    }

    /**
     * Constructor without provided credentials to delegate to the default provider chain.
     * @since 3.1
     */
    constructor(s3ConnectionProperties: S3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties
        amazonS3Client = AmazonS3Client(createConnectionProperties())
        setAmazonS3ConnectionEndpoint()
    }

    constructor(awsCredentials: AwsCredentials?, s3ConnectionProperties: S3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties
        var credentials: AWSCredentials? = null
        if (awsCredentials != null) {
            if (awsCredentials.sessionToken == null) {
                credentials = BasicAWSCredentials(awsCredentials.accessKey, awsCredentials.secretKey)
            } else {
                credentials = BasicSessionCredentials(awsCredentials.accessKey, awsCredentials.secretKey, awsCredentials.sessionToken)
            }
        }
        amazonS3Client = AmazonS3Client(credentials, createConnectionProperties())
        setAmazonS3ConnectionEndpoint()
    }

    private fun setAmazonS3ConnectionEndpoint() {
        val clientOptionsBuilder = S3ClientOptions.builder()
        val endpoint: Optional<URI?> = s3ConnectionProperties.getEndpoint()
        if (endpoint.isPresent()) {
            amazonS3Client.setEndpoint(endpoint.get().toString())
            clientOptionsBuilder.setPathStyleAccess(true).disableChunkedEncoding()
        }
        amazonS3Client.setS3ClientOptions(clientOptionsBuilder.build())
    }

    private fun createConnectionProperties(): ClientConfiguration {
        val clientConfiguration = ClientConfiguration()
        val proxyOptional = s3ConnectionProperties.getProxy()
        if (proxyOptional.isPresent()) {
            val proxy = s3ConnectionProperties.getProxy().get()
            clientConfiguration.setProxyHost(proxy.host)
            clientConfiguration.setProxyPort(proxy.port)
            val credentials = proxy.credentials
            if (credentials != null) {
                clientConfiguration.setProxyUsername(credentials.getUsername())
                clientConfiguration.setProxyPassword(credentials.getPassword())
            }
        }
        val maxErrorRetryCount: Optional<Int?> = s3ConnectionProperties.getMaxErrorRetryCount()
        if (maxErrorRetryCount.isPresent()) {
            clientConfiguration.setMaxErrorRetry(maxErrorRetryCount.get()!!)
        }
        return clientConfiguration
    }

    fun put(inputStream: InputStream?, contentLength: Long, destination: URI?) {
        if (contentLength < s3ConnectionProperties.getMultipartThreshold()) {
            putSingleObject(inputStream, contentLength, destination)
        } else {
            putMultiPartObject(inputStream, contentLength, destination)
        }
    }

    private fun putSingleObject(inputStream: InputStream?, contentLength: Long, destination: URI?) {
        try {
            val s3RegionalResource = S3RegionalResource(destination)
            val bucketName = s3RegionalResource.getBucketName()
            val s3BucketKey = s3RegionalResource.getKey()
            configureClient(s3RegionalResource)

            val objectMetadata = ObjectMetadata()
            objectMetadata.setContentLength(contentLength)

            val putObjectRequest = PutObjectRequest(bucketName, s3BucketKey, inputStream, objectMetadata)
                .withCannedAcl(CannedAccessControlList.BucketOwnerFullControl)
            LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName)

            amazonS3Client.putObject(putObjectRequest)
        } catch (e: AmazonClientException) {
            throw ResourceExceptions.putFailed(destination, e)
        }
    }

    private fun putMultiPartObject(inputStream: InputStream?, contentLength: Long, destination: URI?) {
        try {
            val s3RegionalResource = S3RegionalResource(destination)
            val bucketName = s3RegionalResource.getBucketName()
            val s3BucketKey = s3RegionalResource.getKey()
            configureClient(s3RegionalResource)
            val partETags: MutableList<PartETag?> = ArrayList<PartETag?>()
            val initRequest = InitiateMultipartUploadRequest(bucketName, s3BucketKey)
                .withCannedACL(CannedAccessControlList.BucketOwnerFullControl)
            val initResponse = amazonS3Client.initiateMultipartUpload(initRequest)
            try {
                var filePosition: Long = 0
                var partSize = s3ConnectionProperties.getPartSize()

                LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName)

                var partNumber = 1
                while (filePosition < contentLength) {
                    partSize = min(partSize, contentLength - filePosition)
                    val uploadPartRequest = UploadPartRequest()
                        .withBucketName(bucketName)
                        .withKey(s3BucketKey)
                        .withUploadId(initResponse.getUploadId())
                        .withPartNumber(partNumber)
                        .withPartSize(partSize)
                        .withInputStream(inputStream)
                    partETags.add(amazonS3Client.uploadPart(uploadPartRequest).getPartETag())
                    filePosition += partSize
                    partNumber++
                }

                val completeRequest = CompleteMultipartUploadRequest(
                    bucketName, s3BucketKey, initResponse.getUploadId(), partETags
                )
                amazonS3Client.completeMultipartUpload(completeRequest)
            } catch (e: AmazonClientException) {
                amazonS3Client.abortMultipartUpload(AbortMultipartUploadRequest(bucketName, s3BucketKey, initResponse.getUploadId()))
                throw e
            }
        } catch (e: AmazonClientException) {
            throw ResourceExceptions.putFailed(destination, e)
        }
    }

    fun getMetaData(uri: URI): S3Object? {
        LOGGER.debug("Attempting to get s3 meta-data: [{}]", uri.toString())
        //Would typically use GetObjectMetadataRequest but it does not work with v4 signatures
        return doGetS3Object(uri, true)
    }

    fun getResource(uri: URI): S3Object? {
        LOGGER.debug("Attempting to get s3 resource: [{}]", uri.toString())
        return doGetS3Object(uri, false)
    }

    fun listDirectChildren(parent: URI?): MutableList<String?> {
        val s3RegionalResource = S3RegionalResource(parent)
        val bucketName = s3RegionalResource.getBucketName()
        val s3BucketKey = s3RegionalResource.getKey()
        configureClient(s3RegionalResource)

        val listObjectsRequest = ListObjectsRequest()
            .withBucketName(bucketName)
            .withPrefix(s3BucketKey)
            .withMaxKeys(1000)
            .withDelimiter("/")
        var objectListing = amazonS3Client.listObjects(listObjectsRequest)
        val builder = ImmutableList.builder<String?>()
        builder.addAll(resourceResolver.resolveResourceNames(objectListing))

        while (objectListing.isTruncated()) {
            objectListing = amazonS3Client.listNextBatchOfObjects(objectListing)
            builder.addAll(resourceResolver.resolveResourceNames(objectListing))
        }
        return builder.build()
    }

    private fun doGetS3Object(uri: URI?, isLightWeight: Boolean): S3Object? {
        val s3RegionalResource = S3RegionalResource(uri)
        val bucketName = s3RegionalResource.getBucketName()
        val s3BucketKey = s3RegionalResource.getKey()
        configureClient(s3RegionalResource)

        val getObjectRequest = GetObjectRequest(bucketName, s3BucketKey)
        if (isLightWeight) {
            //Skip content download
            getObjectRequest.setRange(0, 0)
        }

        try {
            return amazonS3Client.getObject(getObjectRequest)
        } catch (e: AmazonServiceException) {
            val errorCode = e.getErrorCode()
            if (null != errorCode && errorCode.equals("NoSuchKey", ignoreCase = true)) {
                return null
            }
            throw ResourceExceptions.getFailed(uri, e)
        }
    }

    private fun configureClient(s3RegionalResource: S3RegionalResource) {
        val endpoint: Optional<URI?> = s3ConnectionProperties.getEndpoint()
        if (endpoint.isPresent()) {
            amazonS3Client.setEndpoint(endpoint.get().toString())
        } else {
            val region: Optional<Region?> = s3RegionalResource.getRegion()
            if (region.isPresent()) {
                amazonS3Client.setRegion(region.get())
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(S3Client::class.java)
    }
}
