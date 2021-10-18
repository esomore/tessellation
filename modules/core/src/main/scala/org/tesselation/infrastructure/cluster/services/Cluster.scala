package org.tesselation.infrastructure.cluster.services

import java.security.KeyPair

import cats.effect.kernel.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, MonadThrow}

import org.tesselation.config.types.AppConfig
import org.tesselation.crypto.Signed
import org.tesselation.domain.cluster.services.Cluster
import org.tesselation.domain.cluster.storage.SessionStorage
import org.tesselation.ext.crypto._
import org.tesselation.infrastructure.gossip.RumorHandler
import org.tesselation.infrastructure.gossip.services.Gossip
import org.tesselation.keytool.security.SecurityProvider
import org.tesselation.kryo.KryoSerializer
import org.tesselation.schema.cluster._
import org.tesselation.schema.peer.{PeerId, RegistrationRequest, SignRequest}

import org.typelevel.log4cats.slf4j.Slf4jLogger

object Cluster {

  def make[F[_]: Async: KryoSerializer: SecurityProvider](
    cfg: AppConfig,
    nodeId: PeerId,
    keyPair: KeyPair,
    sessionStorage: SessionStorage[F],
    gossip: Gossip[F]
  ): F[Cluster[F]] =
    gossip
      .registerHandler(RumorHandler.fromFn { (a: String) =>
        val logger = Slf4jLogger.getLogger[F]
        logger.info(s"Cluster handler for type string. Received=${a}")
      })
      .as(make(cfg, nodeId, keyPair, sessionStorage))

  def make[F[_]: Async: KryoSerializer: SecurityProvider](
    cfg: AppConfig,
    nodeId: PeerId,
    keyPair: KeyPair,
    sessionStorage: SessionStorage[F]
  ): Cluster[F] =
    new Cluster[F] {

      def getRegistrationRequest: F[RegistrationRequest] =
        for {
          session <- sessionStorage.getToken.flatMap {
            case Some(s) => Applicative[F].pure(s)
            case None    => MonadThrow[F].raiseError[SessionToken](SessionDoesNotExist)
          }
        } yield
          RegistrationRequest(
            nodeId,
            cfg.httpConfig.externalIp,
            cfg.httpConfig.publicHttp.port,
            cfg.httpConfig.p2pHttp.port,
            session
          )

      def signRequest(signRequest: SignRequest): F[Signed[SignRequest]] =
        signRequest.sign(keyPair)

    }

}
