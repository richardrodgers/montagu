/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

import io.circe._
import org.http4s._
import org.http4s.util.CaseInsensitiveString
//import org.http4s.circe._
//import org.http4s.server._
import org.http4s.dsl._
import org.http4s.headers.{Accept, Allow, `Content-Length`, `Content-Type`, ETag, Location}
import org.http4s.headers.ETag.EntityTag

import scala.util.Random

import org.eclipse.rdf4j.model.IRI

import ResourceStatus._
import LdpVocabulary._

object LdpServer {

  val ldpRoot = "/ldp/"
  val turtleMt = new MediaType("text", "turtle")
  val resKey = AttributeKey[String]("resource")
  val statKey = AttributeKey[ResourceStatus]("status")

  def optionsHeaders(status: ResourceStatus): Seq[Header] = {
    status match {
      case Container => Allow(GET, HEAD, OPTIONS, POST, PATCH, PUT) ::
                        Header("Accept-Post", "text/turtle, application/ld+json") ::
                        Header("Accept-Patch", "application/sparql-update") :: 
                        Nil
      case _ => Allow(GET, HEAD, OPTIONS, PUT) :: Nil
    }
  }

  def linkHeader(ref: IRI, rel: String): Header = {
    Header("Link", s"<$ref>; rel=$rel")
  }

  def requestMediaType(req: Request): Option[MediaType] = {
    req.headers.get(`Content-Type`) match {
      case Some(ct) => Some(ct.mediaType)
      case _ => None
    }
  }

  def responseMediaType(req: Request): Option[MediaType] = {
    req.headers.get(Accept) match {
      case None => Some(turtleMt)
      case Some(accH) => Some(turtleMt) //Fix me
    }
  }

  def christenResource(req: Request): String = {
    req.headers.get(CaseInsensitiveString("Slug")) match {
      case Some(slug) => slug.value // should sanitize/check
      case _ => Random.alphanumeric.take(8).mkString
    }
  }

  def fullUri(req: Request, res: Option[String]) = {
    val base = req.uri.scheme.getOrElse("http") + "://" + req.uri.authority.getOrElse("localhost")
    val base2 = base.stripSuffix(":8080")
    val addr = res match {
      case Some(str) => base2 + str
      case _ => base2 + req.pathInfo
    }
    addr.stripSuffix("/")
  }

  def resource(req: Request) = req.attributes.get(resKey).get
  def status(req: Request) = req.attributes.get(statKey).get

  def createResource(req : Request) = {
    val newResource = resource(req) + "/" + christenResource(req)
    val contentType = requestMediaType(req).get
    LdpModel.addResource(resource(req), newResource, contentType,
                         scalaz.stream.io.toInputStream(req.body))
    Created().putHeaders(Location(Uri.unsafeFromString(newResource)))
  }

  def updateResource(req: Request) = {
    val contentType = requestMediaType(req).get
    LdpModel.replaceResource(resource(req), status(req), contentType,
                             scalaz.stream.io.toInputStream(req.body))
    Ok().putHeaders(Location(Uri.unsafeFromString(resource(req))))
  }

  val baseService = HttpService {

    case req @ DELETE -> _ =>
      LdpModel.deleteResource(resource(req), status(req))
      NoContent()

    case GET -> Root / "constraints" =>
      Ok("no ldp:contains triples please")
    case req @ GET -> _ =>
      //println("req URI: " + req.uri)
      Ok(LdpModel.getResource(resource(req), status(req)).get)
        .putHeaders(ETag(new EntityTag(req.uri.toString, true)))

    case req @ HEAD -> _ =>
      if (responseMediaType(req).isDefined)
        Ok().putHeaders(ETag(new EntityTag(req.uri.toString, true)),
                        `Content-Length`(0))
      else
        UnsupportedMediaType()

    case OPTIONS -> _ =>
      NoContent()

    case req @ PATCH -> _ =>
      requestMediaType(req) match {
        case Some(MediaType("application", "sparql-update")) =>
          LdpModel.updateRdfSource(resource(req), req.body.toString) match {
            case true => Ok()
            case false => InternalServerError()
          }
        case _ => BadRequest("PATCH needs 'application/sparql-update' entity type")
      }

    case req @ POST -> _ =>
      status(req) match {
        case Container => createResource(req)
        case _ => MethodNotAllowed("Can POST only to a container")
      }

    case req @ PUT -> _ =>
      req.headers.get(CaseInsensitiveString("If-Match")) match {
        case Some(cond) if cond.value != null => updateResource(req)
        case Some(cond) => PreconditionFailed()
        case _ => PreconditionRequired("PUT needs 'If-Match' header")
      }
  }
  // middleware
  def checkResource(service: HttpService): HttpService = Service.lift { req =>
    LdpModel.ensureRoot(fullUri(req, Some(ldpRoot)))
    val resource = fullUri(req, None)
    //println("checkRes: " + resource)
    val stat = LdpModel.status(resource)
    stat match {
      case Unknown => NotFound()
      case Deleted => Gone().putHeaders(`Content-Length`(0))
      case _ => service(req.withAttribute(resKey, resource)
                           .withAttribute(statKey, stat))
                .withAttribute(statKey, stat)
    }
  }

  def responseHeaders(service: HttpService): HttpService = Service.lift { req =>
    service(req).map { resp =>
      if (resp.status.isSuccess) {
        val stat = resp.attributes.get(statKey).get
        resp.putHeaders(`Content-Type`(responseMediaType(req).get),
                        linkHeader(RESOURCE, "type"))
            .putHeaders(optionsHeaders(stat):_*)
      } else resp
    }
  }

  val ldpService = responseHeaders(checkResource(baseService))
}
