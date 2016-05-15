package com.gtan.repox.admin

import java.nio.ByteBuffer
import java.nio.charset.Charset

import io.undertow.server.HttpServerExchange
import io.undertow.util.{Headers, HttpString, StatusCodes}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._


object WebConfigHandler {
  def handle(httpServerExchange: HttpServerExchange) = {
    implicit val exchange = httpServerExchange
    val (method, uriUnprefixed) = (
      exchange.getRequestMethod,
      exchange.getRequestURI.drop("/admin/".length))
    try {
      restHandlers.map(_.route).reduce(_ orElse _).apply(method -> uriUnprefixed)
    } catch {
      case e: Throwable =>
        exchange.setStatusCode(400)
        exchange.getResponseHeaders.add(Headers.CONTENT_TYPE, "text/plain")
        exchange.getResponseSender.send(e.getMessage)
        exchange.endExchange()
    }
  }

  val restHandlers: Seq[RestHandler] = List(
    StaticAssetHandler,
    AuthHandler,
    UpstreamsHandler,
    ConnectorsHandler,
    ProxiesHandler,
    Immediate404RulesHandler,
    ExpireRulesHandler,
    ParametersHandler,
    ResetHandler
  )

  def setConfigAndRespond(exchange: HttpServerExchange, result: Future[Any]): Unit = {
    result.onComplete {
      case Success(_) =>
        respondEmptyOK(exchange)
      case Failure(t) =>
        respondError(exchange, t)
    }
  }

  def isStaticRequest(target: String) = Set(".html", ".css", ".js", ".json", ".ico", ".ttf", ".map", "woff", "woff2", ".svg", "otf", "png", "jpg", "gif").exists(target.endsWith)

  def respondJson[T: Encoder](exchange: HttpServerExchange, data: T): Unit = {
    exchange.setStatusCode(StatusCodes.OK)
    val respondHeaders = exchange.getResponseHeaders
    respondHeaders.put(Headers.CONTENT_TYPE, "application/json")
    val json = data.asJson
    exchange.getResponseChannel.writeFinal(ByteBuffer.wrap(json.noSpaces.getBytes(Charset.forName("UTF-8"))))
    exchange.endExchange()
  }

  def respondError(exchange: HttpServerExchange, t: Throwable): Unit = {
    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
    exchange.getResponseChannel
    exchange.endExchange()
  }

  def respondEmptyOK(exchange: HttpServerExchange): Unit = {
    exchange.setStatusCode(StatusCodes.OK)
    exchange.getResponseChannel
    exchange.endExchange()
  }

}