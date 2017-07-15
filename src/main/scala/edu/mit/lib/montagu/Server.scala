/*
 * Copyright (c) 2017 MIT Libraries
 * Licensed under https://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.montagu

import java.util.concurrent.{ExecutorService, Executors}

import scala.util.Properties.envOrNone

import scalaz.concurrent.Task

import org.http4s.server.{Server, ServerApp}
import org.http4s.server.blaze.BlazeBuilder

object LdpServerApp extends ServerApp {

  val port : Int              = envOrNone("HTTP_PORT") map (_.toInt) getOrElse 8080
  val ip   : String           = "0.0.0.0"
  val pool : ExecutorService  = Executors.newCachedThreadPool()

  override def server(args: List[String]): Task[Server] =
    BlazeBuilder
      .bindHttp(port, ip)
      .mountService(LdpServer.ldpService)
      .withServiceExecutor(pool)
      .start
}
