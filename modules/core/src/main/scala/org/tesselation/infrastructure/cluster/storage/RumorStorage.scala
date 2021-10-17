package org.tesselation.infrastructure.cluster.storage

import cats.effect.kernel.{Async, Spawn, Temporal}
import cats.syntax.all._

import scala.concurrent.duration._

import org.tesselation.crypto.Signed
import org.tesselation.crypto.hash.Hash
import org.tesselation.domain.cluster.storage.RumorStorage
import org.tesselation.schema.gossip.{Rumor, RumorBatch, RumorEntry}

import io.chrisdavenport.mapref.MapRef

object RumorStorage {

  def make[F[_]: Async]: F[RumorStorage[F]] =
    for {
      active <- MapRef.ofConcurrentHashMap[F, Hash, Signed[Rumor]]()
      seen <- MapRef.ofConcurrentHashMap[F, Hash, Unit]()
      storage = new RumorStorage[F] {

        val activeRetention: FiniteDuration = 2.seconds
        val seenRetention: FiniteDuration = 2.minutes

        override def addRumors(rumors: RumorBatch): F[RumorBatch] =
          for {
            unseen <- setRumorsSeenAndGetUnseen(rumors)
            _ <- setRumorsActive(unseen)
          } yield unseen

        override def getRumors(hashes: List[Hash]): F[RumorBatch] =
          hashes.flatTraverse { hash =>
            active(hash).get.map(opt => opt.map(r => List(hash -> r)).getOrElse(List.empty[RumorEntry]))
          }

        override def getActiveHashes: F[List[Hash]] = active.keys

        override def getSeenHashes: F[List[Hash]] = seen.keys

        def setRumorsSeenAndGetUnseen(rumors: RumorBatch): F[RumorBatch] =
          for {
            unseen <- rumors.traverseFilter {
              case p @ (hash, _) =>
                seen(hash).getAndSet(().some).map {
                  case Some(_) => none[RumorEntry]
                  case None    => p.some
                }
            }
          } yield unseen

        def setRumorsActive(rumors: RumorBatch): F[Unit] =
          for {
            _ <- rumors.traverse { case (hash, rumor) => active(hash).set(rumor.some) }
            _ <- Spawn[F].start(setRetention(rumors))
          } yield ()

        def setRetention(rumors: RumorBatch): F[Unit] =
          for {
            _ <- Temporal[F].sleep(activeRetention)
            _ <- rumors.traverse { case (hash, _) => active(hash).set(none[Signed[Rumor]]) }
            _ <- Temporal[F].sleep(seenRetention)
            _ <- rumors.traverse { case (hash, _) => seen(hash).set(none[Unit]) }
          } yield ()
      }
    } yield storage

}
