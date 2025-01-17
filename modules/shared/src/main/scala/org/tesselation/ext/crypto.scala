package org.tesselation.ext

import java.security.KeyPair

import cats.MonadThrow
import cats.effect.kernel.Async
import cats.syntax.either._

import org.tesselation.crypto.hash.Hash
import org.tesselation.crypto.{Hashable, Signed}
import org.tesselation.keytool.security.SecurityProvider
import org.tesselation.kryo.KryoSerializer

object crypto {
  implicit class RefinedHashable[F[_]: KryoSerializer](anyRef: AnyRef) {

    def hash: Either[Throwable, Hash] = Hashable.forKryo[F].hash(anyRef)
  }

  implicit class RefinedHashableF[F[_]: MonadThrow: KryoSerializer](anyRef: AnyRef) {

    def hashF: F[Hash] = Hashable.forKryo[F].hash(anyRef).liftTo[F]
  }

  implicit class RefinedSignedF[F[_]: Async: KryoSerializer: SecurityProvider, A <: AnyRef](data: A) {

    def sign(keyPair: KeyPair): F[Signed[A]] = Signed.forAsyncKryo[F, A](data, keyPair)
  }
}
