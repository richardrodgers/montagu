/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

import java.io.InputStream
import java.nio.file.Files._
import java.nio.file.{Path, Paths}

import scala.util.Properties.envOrNone

import ResourceStatus._

object PairTree extends BinaryStore {
  val rootDir = envOrNone("BINARY_STORE") map { rt =>
    Paths.get(rt)
  } getOrElse Paths.get(System.getProperty("user.dir")).resolve("binstore")
  val root = if (isDirectory(rootDir)) rootDir else createDirectories(rootDir)

  def configured = isDirectory(root)

  def add(resource: String, bits: InputStream): Unit = {
    createDirectories(toPath(clean(resource)))
    copy(bits, pairPath(resource))
  }

  def get(resource: String): Option[InputStream] = {
     val res = pairPath(resource)
     if (exists(res)) Some(newInputStream(res)) else None
  }

  def status(resource: String): ResourceStatus = {
    if (exists(pairPath(resource))) Binary else Unknown
  }

  def delete(resource: String): Unit = {
    deleteIfExists(pairPath(resource))
  }

  private def clean(identifier: String): String =
    identifier.flatMap {
      case t @ ('"' | '*' | '+' | ',' | '<' | '=' | '>' | '?' | '\\' | '^' | '|') =>
        "^" + t.toInt.toHexString
      case c => s"$c"
    } map { case '/' => '=' case ':' => '+' case '.' => ',' case c => c }

  private def pairPath(resource: String): Path = {
    val cleaned = clean(resource)
    toPath(cleaned).resolve(cleaned)
  }

  private def toPath(identifier: String): Path = { // try this, java!
    identifier.sliding(2, 2).foldLeft(root) { (pp, dir) => pp.resolve(dir) }
  }
}
