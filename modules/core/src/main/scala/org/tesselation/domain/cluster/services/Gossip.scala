package org.tesselation.domain.cluster.services
import scala.reflect.runtime.universe.TypeTag

trait Gossip[F[_]] {

  def spread[A <: AnyRef: TypeTag](rumor: A): F[Unit]

}
