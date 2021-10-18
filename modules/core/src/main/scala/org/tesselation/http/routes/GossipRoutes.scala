package org.tesselation.http.routes

import cats.effect.Async
import cats.syntax.flatMap._

import org.tesselation.ext.codecs.BinaryCodec._
import org.tesselation.infrastructure.gossip.services.Gossip
import org.tesselation.kryo.KryoSerializer
import org.tesselation.schema.gossip._

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger

final case class GossipRoutes[F[_]: Async: KryoSerializer](
  gossip: Gossip[F]
) extends Http4sDsl[F] {

  implicit val logger = Slf4jLogger.getLogger[F]

  private[routes] val prefixPath = "/gossip"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "start" =>
      req
        .as[StartGossipRoundRequest]
        .flatMap(gossip.startRound)
        .flatMap(Ok(_))

    case req @ POST -> Root / "end" =>
      req
        .as[EndGossipRoundRequest]
        .flatMap(gossip.endRound)
        .flatMap(Ok(_))
  }

  val p2pRoutes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
