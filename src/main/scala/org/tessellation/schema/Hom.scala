package org.tessellation.schema

import cats.kernel.PartialOrder
import cats.{Applicative, Monad, MonoidK, Traverse, ~>}
import cats.implicits._
import higherkindness.droste._
//import org.tessellation.schema.Cell.NullTerminal
//import org.tessellation.schema.Hom.Endo

/**
 * Characteristic Sheaf just needs to be Poset
 */
trait Poset extends PartialOrder[Ω] {
  override def partialCompare(x: Ω, y: Ω): Double = if (x == y) 0.0 else 1.0
}

/**
  * Terminal object
  */
trait Ω extends Poset

/**
  * Homomorphism object for determining morphism isomorphism
  */
trait Hom[+A, +B] extends Ω {}

//object Hom {
//
//  import cats.syntax.applicative._
//  import cats.syntax.functor._
//
//  type Endo[O] = Hom[Ω, O]
//
//  def apply[A](a: A) = Cell[A, A](a)
//  def apply[A, B](a: A, b: B) = Cell2[A, B](a, b)
//  //todo use fusion to map in between run: Ω => (Hom[A, B], Ω)
//  def apply[A, B](run: A => (Cocell[A, B], B)) = Cocell[A, B](run)
//
//  implicit val arrowInstance: ArrowChoice[Hom] = new ArrowChoice[Hom] with CommutativeArrow[Hom] {
//    override def choose[A, B, C, D](f: Hom[A, C])(g: Hom[B, D]): Hom[Either[A, B], Either[C, D]] = ???
//
//    override def lift[A, B](f: A => B): Hom[A, B] = ???
//
//    override def compose[A, B, C](f: Hom[B, C], g: Hom[A, B]): Hom[A, C] = ???
//
//    override def first[A, B, C](fa: Hom[A, B]): Hom[(A, C), (B, C)] = ???
//  }
//
//  val coalgebra: Coalgebra[Hom[Ω, ?], Ω] = Coalgebra[Hom[Ω, ?], Ω] { thing: Ω =>
//    {
//      println(thing)
//      Cell(thing)
//    }
//  }
//
//  val rcoalgebra: RCoalgebra[Ω, Hom[Ω, ?], Ω] = RCoalgebra {
//    case cell: Cell[Ω, Ω] => {
//      println(cell.a)
//      // Cell2(cell.a, Left(cell.run(cell.a)))
//      Cell0(cell.a)
//    }
//    case ohm: Ω => {
//      println(ohm)
//      Cell(ohm)
//    }
//  }
//
//  val cvAlgebra: CVAlgebra[Hom[Ω, ?], Ω] = CVAlgebra {
//    case cell @ Cell0(a) => {
//      Cell0((a.asInstanceOf[List[Int]]).sum) // consensus execute
//    }
//
//    case cell @ Cell(aa)                         => cell.run(aa)
//    case cell2 @ Cell2(aaa, b :< Cell(c :< _))   => monoidΩ.combine(cell2.run(aaa), b)
//    case cell2 @ Cell2(a, b :< Cell2(c, d :< _)) => monoidΩ.combine(cell2.run(a), monoidΩ.combine(b, c))
//  }
//
//  val ralgebra: RAlgebra[Ω, Hom[Ω, ?], Ω] = RAlgebra {
//    case cell @ Cell(ohm) => cell.run(ohm) //todo cocell here
//    case cell2 @ Cell2(ohm, (b, x)) =>
//      b match {
//        case _: Ω => monoidΩ.combine(cell2.run(ohm), monoidΩ.combine(ohm, x))
//      }
//  }
//
//  val cvCoalgebra = CVCoalgebra[Endo, Ω] {
//    case ohm: Ω => Cell2(ohm, Coattr.pure[Endo, Ω](ohm))
//  }
//
//  implicit val monoidΩ = new Monoid[Ω] {
//    override def empty: Ω = ???
//
//    override def combine(x: Ω, y: Ω): Ω = ???
//  }
//
//  /**
//    * For traversing allong Enrichment
//    *
//    * @tparam A Fixed type
//    * @return
//    */
//  implicit def drosteTraverseForHom[A]: Traverse[Hom[A, *]] =
//    new DefaultTraverse[Hom[A, *]] {
//
//      def traverse[F[_]: Applicative, B, C](
//        fb: Hom[A, B]
//      )(f: B => F[C]): F[Hom[A, C]] =
//        fb match {
//          case Cell2(head, tail) => f(tail).map(Cell2(head, _))
//          case Cell(value)       => (Cell(value): Hom[A, C]).pure[F]
//          case Cell0(value)      => (Cell0(value): Hom[A, C]).pure[F]
//          case Context()         => (Context(): Hom[A, C]).pure[F]
//        }
//    }
//
//  def toScalaList[A, PatR[_[_]]](
//    list: PatR[Hom[A, *]]
//  )(implicit ev: Project[Hom[A, *], PatR[Hom[A, *]]]): List[A] =
//    scheme.cata(toScalaListAlgebra[A]).apply(list)
//
//  def toScalaListAlgebra[A]: Algebra[Hom[A, *], List[A]] = Algebra {
//    case Cell2(head, tail) => head :: tail
//    case Cell(thing)       => thing :: Nil
//    case Context()         => Nil
//  }
//
//  def fromScalaList[A, PatR[_[_]]](
//    list: List[A]
//  )(implicit ev: Embed[Hom[A, *], PatR[Hom[A, *]]]): PatR[Hom[A, *]] =
//    scheme.ana(fromScalaListCoalgebra[A]).apply(list)
//
//  def fromScalaListCoalgebra[A]: Coalgebra[Hom[A, *], List[A]] = Coalgebra {
//    case head :: Nil  => Cell(head)
//    case head :: tail => Cell2(head, tail)
//    case Nil          => Context()
//  }
//
//  implicit def basisHomMonoid[T, A](
//    implicit T: Basis[Hom[A, *], T]
//  ): Monoid[T] =
//    new Monoid[T] {
//      def empty = T.algebra(Context())
//
//      def combine(f1: T, f2: T): T =
//        scheme
//          .cata(Algebra[Hom[A, *], T] {
//            case Context() => f2
//            case cons      => T.algebra(cons)
//          })
//          .apply(f1)
//    }
//
//  // todo this is where we use Poset to order events
//  implicit def drosteDelayEqHom[A](implicit eh: Eq[A]): Delay[Eq, Hom[A, *]] =
//    λ[Eq ~> (Eq ∘ Hom[A, *])#λ](
//      et =>
//        Eq.instance( //todo use BoundedSemilattice here or Semilattice
//          (x, y) =>
//            x match {
//              case Cell2(hx, tx) =>
//                y match {
//                  case Cell2(hy, ty) => eh.eqv(hx, hy) && et.eqv(tx, ty)
//                  case Context()     => false
//                }
//              case Context() =>
//                y match {
//                  case Context() => true
//                  case _         => false
//                }
//            }
//        )
//    )
//}


/*
 *
 */
//todo Cv algebras here

trait ΩList extends Ω

case class ΩCons(h: Ω, t: ΩList) extends ΩList

case object ΩNil extends ΩList


class Cell[M[_] : Monad, F[_] : Traverse, A, B, S](val data: A, val hylo: S => M[B]) extends Topos[A, B] {
  def run(): M[B] = ??? // TODO: We should try to make Cell abstract again and just implement EmptyCell casted back to Cell by asInstanceOf
}

object Cell {
  case class NullTerminal() extends Ω

  def unapply[M[_], F[_], A, B, S](cell: Cell[M, F, A, B, S]): Some[(A, S => M[B])] = Some(cell.data, cell.hylo)

  implicit def cellMonoid[M[_] : Applicative, F[_] : Applicative](implicit M: Monad[M], F: Traverse[F]): MonoidK[Cell[M, F, Ω, Either[CellError, Ω], *]] = {
    new MonoidK[Cell[M, F, Ω, Either[CellError, Ω], *]] {

      override def empty[A]: Cell[M, F, Ω, Either[CellError, Ω], A] = {
        val algebra = AlgebraM[M, F, Either[CellError, Ω]] {
          _ => M.pure(NullTerminal().asInstanceOf[Ω].asRight[CellError])
        }

        val coalgebra = CoalgebraM[M, F, A] {
          _ => M.compose[F].pure(NullTerminal().asInstanceOf[A])
        }

        val hyloM = scheme.hyloM(algebra, coalgebra)

        new Cell[M, F, Ω, Either[CellError, Ω], A](
          NullTerminal(),
          hyloM
        ) {
          override def run(): M[Either[CellError, Ω]] = M.pure(NullTerminal().asInstanceOf[Ω].asRight[CellError])
        }
      }

      // TODO: A param should be Ω as well to make it possible to combine Cells with different A type
      override def combineK[A](
        x: Cell[M, F, Ω, Either[CellError, Ω], A],
        y: Cell[M, F, Ω, Either[CellError, Ω], A]
      ): Cell[M, F, Ω, Either[CellError, Ω], A] = (x, y) match {
        case (Cell(NullTerminal(), _), yy) => yy
        case (xx, Cell(NullTerminal(), _)) => xx
        case (Cell(NullTerminal(), _), Cell(NullTerminal(), _)) => empty[A]
        case (cella@Cell(a, _), cellb@Cell(b, _)) => {
          val input: Ω = ΩCons(a, ΩCons(b, ΩNil))
          val combinedHyloM = (a: A) => {
            for {
              aOutput <- cella.run()
              bOutput <- cellb.run()
              combinedOutput = aOutput.flatMap(aΩ => bOutput.map(bΩ => ΩCons(aΩ, ΩCons(bΩ, ΩNil)).asInstanceOf[Ω]))
            } yield combinedOutput
          }
          new Cell[M, F, Ω, Either[CellError, Ω], A](
            input,
            combinedHyloM
          )
        }
      }
    }
  }
}

////todo postpro/prePro here
//case class Cell2[A, B](override val a: A, b: B) extends Topos[A, B] {}
//
//case class Cell0[A](val a: A) extends Hom[Nothing, Nothing]
//
//case class Context() extends Hom[Nothing, Nothing]

////todo use RAlgebra here, parallel ops
//case class Cocell[A, B](run: A => (Cocell[A, B], B)) extends Hom[A, B] {
//
//  def ifM[A](g: A => A, pred: A => Boolean): A => A = { a =>
//    val newA = g(a)
//    if (pred(newA)) newA else a
//  }
//}

//object Cocell {
//
//  implicit val arrowInstance: Arrow[Cocell] = new Arrow[Cocell] {
//
//    def lift[A, B](f: A => B): Cocell[A, B] = Cocell {
//      lift(f) -> f(_)
//    }
//
//    override def compose[A, B, C](f: Cocell[B, C], g: Cocell[A, B]): Cocell[A, C] = Cocell { a =>
//      val (gg, b) = g.run(a)
//      val (ff, c) = f.run(b)
//      (compose(ff, gg), c)
//    }
//
//    override def first[A, B, C](fa: Cocell[A, B]): Cocell[(A, C), (B, C)] = Cocell {
//      case (a, c) =>
//        val (fa2, b) = fa.run(a)
//        (first(fa2), (b, c))
//    }
//  }
//
//  def runList[A, B](ff: Cocell[A, B], as: List[A]): List[B] = as match {
//    case h :: t =>
//      val (ff2, b) = ff.run(h)
//      b :: runList(ff2, t)
//    case _ => List()
//  }
//
//  def accum[A, B](b: B)(f: (A, B) => B): Cocell[A, B] = Cocell { a =>
//    val b2 = f(a, b)
//    (accum(b2)(f), b2)
//  }
//
//  def sum[A: Monoid]: Cocell[A, A] = accum(Monoid[A].empty)(_ |+| _)
//
//  def count[A]: Cocell[A, Int] = Arrow[Cocell].lift((_: A) => 1) >>> sum
//
//  def avg: Cocell[Int, Double] =
//    Topos.combine(sum[Int], count[Int]) >>> Arrow[Cocell].lift {
//      case (x, y) => x.toDouble / y
//    }
//
////  def runList[A, B](ff: Cocell[A, B], as: List[A]): List[B] = as match {
////    case h :: t =>
////      val (ff2, b) = ff.run(h)
////      b :: runList(ff2, t)
////    case _ => List()
////  }
//
//  def runEndo[A, B](ff: Cocell[A, B], as: Endo[A]): Endo[B] = ???
//}

abstract class Fiber[A, B]

abstract class Bundle[F, G](fibers: F)

//todo use Kleisli like gRPC server? SimplexServer[Kleisli[IO, Span[IO], *]]
abstract class Simplex[T, U, V](fibers: Seq[Hom[T, U]])

