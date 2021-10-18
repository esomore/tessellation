package org.tesselation.infrastructure.gossip.services

import java.security.KeyPair

import cats.effect._
import cats.effect.std.{Queue, Random}
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.semigroupk._
import cats.syntax.traverseFilter._
import cats.{Applicative, Parallel}

import scala.concurrent.duration._
import scala.reflect.runtime.universe._

import org.tesselation.crypto.hash.Hash
import org.tesselation.domain.cluster.storage.ClusterStorage
import org.tesselation.domain.gossip.storage.RumorStorage
import org.tesselation.ext.crypto._
import org.tesselation.ext.kryo._
import org.tesselation.http.p2p.clients.GossipClient
import org.tesselation.infrastructure.gossip.RumorHandler
import org.tesselation.keytool.security.SecurityProvider
import org.tesselation.kryo.KryoSerializer
import org.tesselation.schema.gossip._
import org.tesselation.schema.peer.Peer

import fs2.{Pipe, Stream}
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Gossip {

  def make[F[_]: Async: Parallel: Random: SecurityProvider: KryoSerializer](
    rumorStorage: RumorStorage[F],
    clusterStorage: ClusterStorage[F],
    gossipClient: GossipClient[F],
    keyPair: KeyPair
  ): F[Gossip[F]] =
    Queue.unbounded[F, RumorBatch] >>= { rumorQueue =>
      Ref.of[F, Set[RumorHandler[F]]](Set.empty) >>= { rumorHandlers =>
        make(rumorQueue, rumorHandlers, rumorStorage, clusterStorage, gossipClient, keyPair)
      }
    }

  def make[F[_]: Async: Parallel: Random: SecurityProvider: KryoSerializer](
    rumorQueue: Queue[F, RumorBatch],
    rumorHandlers: Ref[F, Set[RumorHandler[F]]],
    rumorStorage: RumorStorage[F],
    clusterStorage: ClusterStorage[F],
    gossipClient: GossipClient[F],
    keyPair: KeyPair
  ): F[Gossip[F]] = {
    val fanout: Int = 2
    val interval: FiniteDuration = 0.2.second
    val maxConcurrentHandlers: Int = 20

    val logger = Slf4jLogger.getLogger[F]

    val gossip = new Gossip(rumorQueue, rumorHandlers, rumorStorage, keyPair) {}

    def pipe: Pipe[F, RumorBatch, Unit] =
      in =>
        in.evalMap { _.pure[F] }.evalMap {
          _.filterA {
            case (hash, signedRumor) =>
              signedRumor.value.hashF.map(_ == hash)
          }
        }.evalMap { batch =>
          // TODO: filter out invalid signatures
          Async[F].pure(batch)
        }.flatMap { Stream.iterable(_) }
          .parEvalMapUnordered(maxConcurrentHandlers) {
            case (hash, signedRumor) =>
              val rumor = signedRumor.value
              rumorHandlers.get
                .map(_.reduce(_ <+> _))
                .flatMap(_.run(signedRumor.value).getOrElseF {
                  logger.warn(s"Unhandled rumor of type ${rumor.tpe} with hash $hash.")
                }.handleErrorWith { err =>
                  logger.error(err)(
                    s"Error handling rumor of type ${rumor.tpe} with hash $hash"
                  )
                })
          }

    val consume = Stream
      .fromQueueUnterminated(rumorQueue)
      .through(pipe)
      .compile
      .drain

    def runGossipRounds(activeHashes: List[Hash]): F[Unit] =
      for {
        seenHashes <- rumorStorage.getSeenHashes
        peers <- clusterStorage.getPeers
        selectedPeers <- Random[F].shuffleList(peers.toList).map(_.take(fanout))
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

    def spread: F[Unit] =
      for {
        _ <- Temporal[F].sleep(interval)
        activeHashes <- rumorStorage.getActiveHashes
        _ <- if (activeHashes.nonEmpty) runGossipRounds(activeHashes)
        else Applicative[F].unit
      } yield ()

    (Spawn[F].start(spread.foreverM) >> Spawn[F].start(consume)).as(gossip)
  }
}

sealed abstract class Gossip[F[_]: Async: SecurityProvider: KryoSerializer] private (
  rumorQueue: Queue[F, RumorBatch],
  handlers: Ref[F, Set[RumorHandler[F]]],
  rumorStorage: RumorStorage[F],
  keyPair: KeyPair
) {

  def registerHandler(handler: RumorHandler[F]): F[Unit] =
    handlers.update(_ + handler)

  def spread[A <: AnyRef: TypeTag](rumorContent: A): F[Unit] =
    for {
      contentBinary <- rumorContent.toBinaryF
      rumor = Rumor(typeOf[A].toString, contentBinary)
      hash <- rumor.hashF
      signedRumor <- rumor.sign(keyPair)
      _ <- rumorQueue.offer(List(hash -> signedRumor))
    } yield ()

  def startRound(request: StartGossipRoundRequest): F[StartGossipRoundResponse] =
    for {
      activeHashes <- rumorStorage.getActiveHashes
      offer = activeHashes.diff(request.offer)
      seenHashes <- rumorStorage.getSeenHashes
      inquiry = request.offer.diff(seenHashes)
    } yield StartGossipRoundResponse(inquiry, offer)

  def endRound(request: EndGossipRoundRequest): F[EndGossipRoundResponse] =
    for {
      _ <- rumorQueue.offer(request.answer)
      answer <- rumorStorage.getRumors(request.inquiry)
    } yield EndGossipRoundResponse(answer)
}
