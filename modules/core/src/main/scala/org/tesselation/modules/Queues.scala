package org.tesselation.modules

import cats.effect.kernel.Concurrent
import cats.effect.std.Queue
import cats.syntax.functor._

import org.tesselation.schema.gossip.RumorBatch

object Queues {

  def make[F[_]: Concurrent]: F[Queues[F]] =
    for {
      rumorQueue <- Queue.unbounded[F, RumorBatch]
    } yield
      new Queues[F] {
        override val rumor: Queue[F, RumorBatch] = rumorQueue
      }
}

sealed abstract class Queues[F[_]] private {
  val rumor: Queue[F, RumorBatch]
}
