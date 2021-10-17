package org.tesselation.infrastructure.gossip

import cats.effect.Async
import cats.effect.kernel.{Spawn, Temporal}
import cats.effect.std.{Queue, Random}
import cats.syntax.all._
import cats.{Applicative, Parallel}

import scala.concurrent.duration._

import org.tesselation.crypto.hash.Hash
import org.tesselation.domain.cluster.storage.{ClusterStorage, RumorStorage}
import org.tesselation.ext.crypto._
import org.tesselation.http.p2p.clients.GossipClient
import org.tesselation.kryo.KryoSerializer
import org.tesselation.schema.gossip.{EndGossipRoundRequest, RumorBatch, StartGossipRoundRequest}
import org.tesselation.schema.peer.Peer

import fs2._
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait GossipDaemon[F[_]] {
  def startDaemon: F[Unit]
}

object GossipDaemon {

  def make[F[_]: Async: KryoSerializer: Random: Parallel](
    rumorStorage: RumorStorage[F],
    rumorQueue: Queue[F, RumorBatch],
    clusterStorage: ClusterStorage[F],
    gossipClient: GossipClient[F],
    rumorHandler: RumorHandler[F]
  ): GossipDaemon[F] = new GossipDaemon[F] {
    val fanOut: Int = 2
    val interval: FiniteDuration = 0.2.second
    val maxConcurrentHandlers: Int = 20

    private val logger = Slf4jLogger.getLogger[F]

    override def startDaemon: F[Unit] =
      for {
        _ <- Spawn[F].start(spreadActiveRumors.foreverM).void
        _ <- Spawn[F].start(consumeRumors)
      } yield ()

    def consumeRumors: F[Unit] =
      Stream
        .fromQueueUnterminated(rumorQueue)
        .evalMap { batch =>
          batch.filterA {
            case (hash, signedRumor) =>
              signedRumor.value.hashF.map(_ == hash)
          }
        }
        .evalMap { batch =>
          // TODO: filter out invalid signatures
          Async[F].pure(batch)
        }
        .evalMap(rumorStorage.addRumors)
        .flatMap(batch => Stream.iterable(batch))
        .parEvalMapUnordered(maxConcurrentHandlers) {
          case (hash, signedRumor) =>
            val rumor = signedRumor.value
            rumorHandler
              .run(signedRumor.value)
              .getOrElseF {
                logger.warn(s"Unhandled rumor of type ${rumor.tpe} with hash $hash.")
              }
              .handleErrorWith { err =>
                logger.error(err)(
                  s"Error handling rumor of type ${rumor.tpe} with hash $hash"
                )
              }
        }
        .compile
        .drain

    def spreadActiveRumors: F[Unit] =
      for {
        _ <- Temporal[F].sleep(interval)
        activeHashes <- rumorStorage.getActiveHashes
        _ <- if (activeHashes.nonEmpty) runGossipRounds(activeHashes)
        else Applicative[F].unit
      } yield ()

    def runGossipRounds(activeHashes: List[Hash]): F[Unit] =
      for {
        seenHashes <- rumorStorage.getSeenHashes
        peers <- clusterStorage.getPeers
        selectedPeers <- Random[F].shuffleList(peers.toList).map(_.take(fanOut))
        _ <- selectedPeers.parTraverse(runGossipRound(activeHashes, seenHashes)).void
      } yield ()

    def runGossipRound(activeHashes: List[Hash], seenHashes: List[Hash])(peer: Peer): F[Unit] =
      for {
        _ <- Applicative[F].unit
        startRequest = StartGossipRoundRequest(activeHashes)
        startResponse <- gossipClient.startGossiping(startRequest).run(peer)
        inquiry = startResponse.offer.diff(seenHashes)
        answer <- rumorStorage.getRumors(startResponse.inquiry)
        endRequest = EndGossipRoundRequest(answer, inquiry)
        endResponse <- gossipClient.endGossiping(endRequest).run(peer)
        _ <- rumorQueue.offer(endResponse.answer)
      } yield ()
  }

}
