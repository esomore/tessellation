package org.tesselation.modules

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._

import org.tesselation.domain.cluster.programs.{Joining, PeerDiscovery}
import org.tesselation.effects.GenUUID
import org.tesselation.http.p2p.P2PClient
import org.tesselation.keytool.security.SecurityProvider
import org.tesselation.kryo.KryoSerializer
import org.tesselation.schema.peer.PeerId

object Programs {

  def make[F[_]: Async: SecurityProvider: KryoSerializer](
    storages: Storages[F],
    services: Services[F],
    p2pClient: P2PClient[F],
    nodeId: PeerId
  ): F[Programs[F]] =
    for {
      pd <- PeerDiscovery.make(p2pClient, storages.cluster, nodeId)
      joining <- Joining.make(
        storages.node,
        storages.cluster,
        p2pClient,
        services.cluster,
        services.session,
        pd
      )
    } yield new Programs[F](pd, joining) {}
}

sealed abstract class Programs[F[_]: Async: GenUUID: SecurityProvider: KryoSerializer] private (
  val peerDiscovery: PeerDiscovery[F],
  val joining: Joining[F]
) {}
