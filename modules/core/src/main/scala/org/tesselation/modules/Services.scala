package org.tesselation.modules

import java.security.KeyPair

import cats.Parallel
import cats.effect.Async
import cats.effect.std.Random
import cats.syntax.flatMap._
import cats.syntax.functor._

import org.tesselation.config.types.AppConfig
import org.tesselation.domain.cluster.services.{Cluster, Session}
import org.tesselation.domain.healthcheck.HealthCheck
import org.tesselation.http.p2p.P2PClient
import org.tesselation.infrastructure.cluster.services.{Cluster, Session}
import org.tesselation.infrastructure.gossip.services.Gossip
import org.tesselation.infrastructure.healthcheck.HealthCheck
import org.tesselation.keytool.security.SecurityProvider
import org.tesselation.kryo.KryoSerializer
import org.tesselation.schema.peer.PeerId

object Services {

  def make[F[_]: Async: Parallel: Random: KryoSerializer: SecurityProvider](
    cfg: AppConfig,
    nodeId: PeerId,
    keyPair: KeyPair,
    storages: Storages[F],
    p2pClient: P2PClient[F]
  ): F[Services[F]] =
    for {
      gossip <- Gossip.make(
        storages.rumor,
        storages.cluster,
        p2pClient.gossip,
        keyPair
      )
      healthcheck = HealthCheck.make[F]
      session = Session.make[F](storages.session, storages.cluster, storages.node)
      cluster <- Cluster
        .make[F](cfg, nodeId, keyPair, storages.session, gossip)
    } yield
      new Services[F](
        gossip = gossip,
        healthcheck = healthcheck,
        cluster = cluster,
        session = session
      ) {}
}

sealed abstract class Services[F[_]] private (
  val gossip: Gossip[F],
  val healthcheck: HealthCheck[F],
  val cluster: Cluster[F],
  val session: Session[F]
)
