package org.tessellation.schema
import cats.{Functor, Representable, ~>}
import cats.kernel.PartialOrder
import org.tessellation.schema.Topos.Enriched

class DAG extends Topos {
  implicit val repr = new Representable[Enriched] {
      override def F: Functor[Enriched] = ???

      override type Representation = this.type

      override def index[A](f: Enriched[A]): this.type => A = ???
      // https://ncatlab.org/nlab/show/2-sheaf
      // https://ncatlab.org/nlab/show/indexed+category

      override def tabulate[A](f: this.type => A): Enriched[A] = ???
    }

  val representation: Representable[Enriched] = Representable(repr)
}

class Topology extends PartialOrder[Ω]{
  override def partialCompare(x: Ω,//todo define domain on omega
                              y: Ω): Double = if (x == y) 0.0 else 1.0
}
