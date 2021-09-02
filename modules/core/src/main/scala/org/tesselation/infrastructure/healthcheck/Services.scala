package org.tesselation.infrastructure.healthcheck

import cats.effect.kernel.Temporal

import org.tesselation.domain.healthcheck.HealthCheck

object Services {

  def make[F[_]: Temporal]: Services[F] =
    new Services[F](
      healthCheck = HealthCheck.make[F]()
    ) {}
}

sealed abstract class Services[F[_]] private (
  val healthCheck: HealthCheck[F]
)