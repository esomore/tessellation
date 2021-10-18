package org.tesselation.domain.gossip.services

import scala.reflect.runtime.universe.TypeTag

import org.tesselation.infrastructure.gossip.RumorHandler
import org.tesselation.schema.gossip._

trait Gossip[F[_]] {
  def registerHandler(handler: RumorHandler[F]): F[Unit]
  def spread[A <: AnyRef: TypeTag](rumorContent: A): F[Unit]
  def startRound(request: StartGossipRoundRequest): F[StartGossipRoundResponse]
  def endRound(request: EndGossipRoundRequest): F[EndGossipRoundResponse]
}
