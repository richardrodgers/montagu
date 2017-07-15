/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.util.{HashSet, Set => JSet}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Set => MSet}

import org.eclipse.rdf4j.model.{IRI, Statement, Value => MValue, ValueFactory}
import org.eclipse.rdf4j.model.impl.{ContextStatement, SimpleValueFactory}
import org.eclipse.rdf4j.rio.{RDFFormat, RDFWriterFactory, RDFParserRegistry, RDFWriterRegistry}
import org.eclipse.rdf4j.rio.helpers.StatementCollector

import org.http4s.MediaType

trait ResourceStore {
  import ResourceStatus._
  def status(resource: String): ResourceStatus
  def delete(resource: String): Unit
}

trait RdfStore extends ResourceStore {
  def valueFactory(): ValueFactory
  def add(resource: String, stmts: MSet[Statement]): Unit
  def get(resource: String): JSet[Statement]
}

trait BinaryStore extends ResourceStore {
  def add(resource: String, bits: InputStream): Unit
  def get(resource: String): Option[InputStream]
}

object LdpModel {
  import LdpVocabulary._
  import ResourceStatus._

  val rdfStore = SailRepository
  val binaryStore = if (S3Store.configured) S3Store else PairTree
  val valueFact = rdfStore.valueFactory
  val writerReg = RDFWriterRegistry.getInstance
  val parserReg = RDFParserRegistry.getInstance
  var rootChecked = false

  def getResource(resource: String, status: ResourceStatus): Option[InputStream] = {
    //println("getResource: " + resource + ": " + status)
    status match {
      case Container => Some(serialize(rdfStore.get(resource).asScala, List("text/turtle")))
      case Binary => binaryStore.get(resource)
      case _ => binaryStore.get(resource)
    }
  }

  def addResource(parent: String, resource: String,
                  contentType: MediaType, content: InputStream): Unit = {
    // binary or RDF? - known RDF content-Types are the test
    val parentIri = valueFact.createIRI(parent)
    val res = valueFact.createIRI(resource)
    val stmts = MSet[Statement]()
    contentType match {
      case (MediaType("text", "turtle") | MediaType("application", "ld+json")) => {
        val pstmts = parse(content, mtFormat(contentType), resource)
        stmts ++= pstmts.map( st => valueFact.createStatement(st.getSubject(), st.getPredicate(), st.getObject(), INTERNAL_CONTEXT))
        // add server-managed statements
        stmts += valueFact.createStatement(res, RDF_TYPE, RDF_SOURCE, INTERNAL_CONTEXT)
      }
      case _ => {
        // add server-managed statements, then content to binary store
        stmts += valueFact.createStatement(res, RDF_TYPE, NONRDF_SOURCE, INTERNAL_CONTEXT)
        binaryStore.add(resource, content)
      }
    }
    // add common server-managed statements
    stmts += valueFact.createStatement(res, RDF_TYPE, RESOURCE, INTERNAL_CONTEXT)
    stmts += valueFact.createStatement(parentIri, CONTAINS, res, INTERNAL_CONTEXT)
    rdfStore.add(resource, stmts)
  }

  def status(resource: String): ResourceStatus = {
    rdfStore.status(resource)
  }

  def deleteResource(resource: String, status: ResourceStatus): Unit = {
    status match {
      case Binary => binaryStore.delete(resource)
      case Container => rdfStore.delete(resource)
      case ( Unknown | Deleted ) =>
    }
  }

  def replaceResource(resource: String, status: ResourceStatus,
                      contentType: MediaType, content: InputStream): Unit = {
    status match {
      case Binary => {
        binaryStore.delete(resource)
        binaryStore.add(resource, content)
      }
      case Container => {
        val stmts = parse(content, mtFormat(contentType), resource)
        rdfStore.replace(resource, stmts)
      }
      case ( Unknown | Deleted ) =>
    }
  }

  def updateRdfSource(resource: String, query: String): Boolean = ???
  def replace(resource: String, stmts: JSet[Statement]): Unit = ???

  def ensureRoot(root: String): Unit = {
    if (! rootChecked && status(root) == Unknown) {
      val subject = valueFact.createIRI(root)
      val stmts = MSet[Statement]()
      stmts += valueFact.createStatement(subject, RDF_TYPE, RDF_SOURCE, null)
      stmts += valueFact.createStatement(subject, RDF_TYPE, BASIC_CONTAINER, INTERNAL_CONTEXT)
      stmts += valueFact.createStatement(subject, RDF_TYPE, RDF_SOURCE, INTERNAL_CONTEXT)
      stmts += valueFact.createStatement(subject, RDF_TYPE, RESOURCE, INTERNAL_CONTEXT)
      rdfStore.add(subject.toString(), stmts)
      rootChecked = true
    }
  }

  private def parse(inputStream: InputStream, format: RDFFormat, resource: String): MSet[Statement] = {
    val parser = parserReg.get(format).get().getParser()
    val stmts = new HashSet[Statement]
    parser.setRDFHandler(new StatementCollector(stmts))
    parser.parse(inputStream, resource)
    stmts.asScala
  }

  private def serialize(stmts: MSet[Statement], fmts: List[String]): InputStream = {
    //val fmt: RDFFormat = fmts.find(reg.getFileFormatForMIMEType(_) != null).getOrElse(RDFFormat.TURTLE)
    val fmt = RDFFormat.TURTLE
    val out = new ByteArrayOutputStream
    val writer = writerReg.get(fmt).get().getWriter(out)
    writer.startRDF
    stmts.foreach(writer.handleStatement(_))
    writer.endRDF
    new ByteArrayInputStream(out.toByteArray)
  }

  private def mtFormat(mediaType: MediaType): RDFFormat = {
    mediaType match {
      case MediaType("text", "turtle") => RDFFormat.TURTLE
      case MediaType("application", "ld+json") => RDFFormat.JSONLD
      case _ => RDFFormat.TURTLE
    }
  }
}

object ResourceStatus extends Enumeration {
  import LdpVocabulary._
  type ResourceStatus = Value
  val Unknown, Deleted, Binary, Container = Value
  def fromIRI(status: MValue): ResourceStatus = {
    status match {
      case DELETED => Deleted
      case _ => Unknown
    }
  }
}

object LdpVocabulary {
  val fact = SimpleValueFactory.getInstance
  val ldp_ns = "http://www.w3.org/ns/ldp"
  val rdf_ns = "http://www.w3.org/1999/02/22-rdf-syntax-ns"
  val LDP_NS = fact.createIRI(ldp_ns);
  val IANA_TYPE = fact.createIRI("http://www.iana.org/assignments/relation/type")
  val RDF_TYPE = fact.createIRI(rdf_ns+"#type")
  val RESOURCE = fact.createIRI(ldp_ns+"#Resource")
  val BASIC_CONTAINER = fact.createIRI(ldp_ns+"#BasicContainer")
  val CONTAINER = fact.createIRI(ldp_ns+"#Container")
  val DELETED = fact.createIRI(ldp_ns+"#DeletedResource")
  val CONTAINS = fact.createIRI(ldp_ns+"#contains")
  val RDF_SOURCE = fact.createIRI(ldp_ns+"#RDFSource")
  val NONRDF_SOURCE = fact.createIRI(ldp_ns+"#NonRDFSource")
  val INTERNAL_CONTEXT = fact.createIRI("info:montagu/")
}
