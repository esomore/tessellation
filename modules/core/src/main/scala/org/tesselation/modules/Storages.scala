package org.tesselation.modules

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._

import org.tesselation.domain.cluster.storage._
import org.tesselation.domain.gossip.storage.RumorStorage
import org.tesselation.domain.node.NodeStorage
import org.tesselation.infrastructure.cluster.storage._
import org.tesselation.infrastructure.db.doobie.DoobieTransactor
import org.tesselation.infrastructure.gossip.storage.RumorStorage
import org.tesselation.infrastructure.node.NodeStorage

object Storages {

  def make[F[_]: Async: DoobieTransactor]: F[Storages[F]] =
    for {
      addressStorage <- AddressStorage.make[F]
      clusterStorage <- ClusterStorage.make[F]
      nodeStorage <- NodeStorage.make[F]
      sessionStorage <- SessionStorage.make[F]
      rumorStorage <- RumorStorage.make[F]
    } yield
      new Storages[F](
        address = addressStorage,
        cluster = clusterStorage,
        node = nodeStorage,
        session = sessionStorage,
        rumor = rumorStorage
      ) {}
}

sealed abstract class Storages[F[_]] private (
  val address: AddressStorage[F],
  val cluster: ClusterStorage[F],
  val node: NodeStorage[F],
  val session: SessionStorage[F],
  val rumor: RumorStorage[F]
)
