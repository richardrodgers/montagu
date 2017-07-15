/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

//import io.circe._
import org.http4s._
import org.http4s.util.CaseInsensitiveString
//import org.http4s.circe._
//import org.http4s.server._
import org.http4s.dsl._
import org.http4s.headers.{Accept, Allow, `Content-Length`, `Content-Type`, ETag, Location}
import org.http4s.headers.ETag.EntityTag

import org.eclipse.rdf4j.model.IRI

import org.scalatest.FlatSpec

import ResourceStatus._
import LdpVocabulary._

class LdpServerTest extends FlatSpec {

  val turtleMt = new MediaType("text", "turtle")
  val baseUri = uri("http://foo.bar/foo")
  val service = LdpServer.ldpService

  "An LDP server" should "advertise LDP support" in {
    val req = Request(GET, baseUri)
    val res = service.run(req).run
    val link = res.headers.get(CaseInsensitiveString("Link")).get
    assert(link == LdpServer.linkHeader(RESOURCE, "type"))
  }

  "An unknown request" should "404" in {
    val req = Request(GET, baseUri)
    val res = service.run(req).run
    assert(res.status == Ok)
  }

  "An OPTIONS request" should "return no content" in {
    val req = Request(OPTIONS, baseUri)
    val res = service.run(req).run
    assert(res.status == NoContent)
  }

  "A HEAD request" should "return Ok" in {
    val req = Request(HEAD, baseUri)
    val res = service.run(req).run
    assert(res.status == Ok)
  }

  "A PATCH without Content-Type 'application/sparql-update'" should "fail" in {
    val req = Request(PATCH, uri("http://foo.bar/foo"))
              .putHeaders(`Content-Type`(new MediaType("application","json")))
    val res = service.run(req).run
    assert(res.status == BadRequest)
  }

}
