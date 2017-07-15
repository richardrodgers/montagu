/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

import java.io.InputStream

import scala.util.Properties.envOrElse

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata

import ResourceStatus._

object S3Store extends BinaryStore {
  val bucket = envOrElse("AWS_S3_BUCKET_NAME", "invalid")
  lazy val s3 = AmazonS3ClientBuilder.defaultClient

  def configured = {
    if ("invalid" == bucket) false
    else {
      if (!s3.doesBucketExist(bucket)) {
        s3.createBucket(bucket)
      }
      s3.doesBucketExist(bucket)
    }
  }

  def add(resource: String, bits: InputStream): Unit = {
    s3.putObject(bucket, resource, bits, new ObjectMetadata)
  }

  def get(resource: String): Option[InputStream] = {
    s3.doesObjectExist(bucket, resource) match {
      case false => None
      case true => Some(s3.getObject(bucket, resource).getObjectContent)
    }
  }

  def status(resource: String): ResourceStatus = {
    if (s3.doesObjectExist(bucket, resource)) Binary else Unknown
  }

  def delete(resource: String): Unit = {
    s3.deleteObject(bucket, resource)
  }
}
