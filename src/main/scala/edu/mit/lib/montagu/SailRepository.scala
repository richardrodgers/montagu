/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

import java.io.File
import java.util.{HashSet, Set => JSet}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Set => MSet, MutableList => MList}
import scala.util.Properties.envOrNone

import org.eclipse.rdf4j.model.{IRI, Resource, Statement, ValueFactory}
import org.eclipse.rdf4j.query.parser.ParsedUpdate
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection, RepositoryResult}
import org.eclipse.rdf4j.repository.sail.{SailRepository => JSailRepository}
import org.eclipse.rdf4j.repository.util.Repositories
import org.eclipse.rdf4j.rio.{ParserConfig, Rio, RDFFormat, RDFHandler}
//import org.eclipse.rdf4j.sail.SailConnectionUpdate
import org.eclipse.rdf4j.sail.memory.MemoryStore

import LdpVocabulary._
import ResourceStatus._

//import edu.mit.lib.montagu.RdfResourceStore

object SailRepository extends RdfStore {
  val store = envOrNone("RDF_STORE") map {
    st => new MemoryStore(new File(st))
  } getOrElse new MemoryStore
  val repo: Repository = new JSailRepository(store)
  repo.initialize()
  val vfact = repo.getValueFactory
  def valueFactory() = vfact

  def add(resource: String, stmts: MSet[Statement]) = {
    val res = vfact.createIRI(resource)
    //println("Sail Repo add: " + resource + ": " + stmts.size)
    Repositories.consume( repo, conn => {
      stmts.foreach(conn.add(_))
    })
  }

  def get(resource: String): JSet[Statement] = {
    val res = vfact.createIRI(resource)
    val stmts = Repositories.get(repo, conn => {
      // debug - show whole repo
      //val writer: RDFHandler = Rio.createWriter(RDFFormat.TURTLE, System.out)
      // conn.export(writer, INTERNAL_CONTEXT)
      copyResult(conn.getStatements(res, null, null, false))
    })
    new HashSet(stmts.asJava)
  }

  def status(resource: String): ResourceStatus = {
    val res = vfact.createIRI(resource)
    Repositories.get(repo, conn => {
      // debug - show whole repo
      // val writer: RDFHandler = Rio.createWriter(RDFFormat.TURTLE, System.out)
      // conn.export(writer, INTERNAL_CONTEXT)
      if (conn.getStatements(res, RDF_TYPE, RESOURCE, INTERNAL_CONTEXT).hasNext()) {
        if (conn.getStatements(res, RDF_TYPE, RDF_SOURCE, INTERNAL_CONTEXT).hasNext()) {
          Container
        } else Binary
      } else if (conn.getStatements(res, RDF_TYPE, DELETED, INTERNAL_CONTEXT).hasNext()) {
        Deleted
      } else Unknown
    })
  }

  def replace(resource: String, stmts: MSet[Statement]) = {
    val res = vfact.createIRI(resource)
    Repositories.consume( repo, conn => {
      conn.remove(res, null, null)
      stmts.foreach(conn.add(_))
    })
  }

  def updateResource(resource: String, query: String) = {
    val parsedUpdate = new ParsedUpdate(query)
    Repositories.consume(repo, conn => {
      //new SailConnectionUpdate(parsedUpdate, conn, vfact, new ParserConfig()).execute
    })
    true
  }

  def delete(resource: String) = {
    val res = vfact.createIRI(resource)
    val anySubject: Resource = null
    Repositories.consume( repo, conn => {
      conn.remove(res, null, null)
      conn.remove(anySubject, CONTAINS, res)
      conn.add(res, RDF_TYPE, DELETED, INTERNAL_CONTEXT) // tombstone
    })
  }

  private def copyResult(result: RepositoryResult[Statement]): Seq[Statement] = {
    var resList = MList[Statement]()
    while (result.hasNext()) {
        resList += result.next()
    }
    resList
  }
}
