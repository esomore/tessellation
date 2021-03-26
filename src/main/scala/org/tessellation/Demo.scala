package org.tessellation

import cats.effect.concurrent.Semaphore
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import fs2.{Pull, Stream}
import org.tessellation.consensus.transaction.RandomTransactionGenerator
import org.tessellation.consensus._
import org.tessellation.schema.{CellError, Ω}
import org.tessellation.snapshot.{L0Cell, L0Edge}

import scala.concurrent.duration._

object SingleL1ConsensusDemo extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      nodeA <- Node.run("nodeA", "A")
      nodeB <- Node.run("nodeB", "B")
      nodeC <- Node.run("nodeC", "C")

      _ <- nodeA.joinTo(Set(nodeB, nodeC))
      _ <- nodeB.joinTo(Set(nodeA, nodeC))
      _ <- nodeC.joinTo(Set(nodeA, nodeB))

      txs = Set(L1Transaction(12, "a", "b", "", 0))

      // cell pool
      cell = L1Cell(L1Edge(txs))

      block <- nodeA.startL1Consensus(cell)

      _ = Log.magenta(s"Output: ${block}")

    } yield ExitCode.Success
}

object WaitingPoolDemo extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val maxRoundsInProgress = 2
    val parallelJobs = 3
    val edgeFactory = L1EdgeFactory("nodeA")

    val cluster: Stream[IO, (Node, Node, Node)] = Stream.eval {
      for {
        nodeA <- Node.run("nodeA", "A")
        nodeB <- Node.run("nodeB", "B")
        nodeC <- Node.run("nodeC", "C")

        _ <- nodeA.joinTo(Set(nodeB, nodeC))
        _ <- nodeB.joinTo(Set(nodeA, nodeC))
        _ <- nodeC.joinTo(Set(nodeA, nodeB))
      } yield (nodeA, nodeB, nodeC)
    }

    def runPredefinedScenario: Stream[IO, L1Transaction] = {
      val tx1 = L1Transaction(1, "A", "B", parentHash = "", 0)
      val tx2 = L1Transaction(2, "A", "B", parentHash = tx1.hash, 1)
      val tx3 = L1Transaction(3, "A", "B", parentHash = tx2.hash, 2)
      val tx4 = L1Transaction(4, "B", "C", parentHash = "", 0)
      val tx5 = L1Transaction(5, "B", "C", parentHash = tx4.hash, 1)
      val tx6 = L1Transaction(6, "B", "C", parentHash = tx5.hash, 2)

      Stream.emits(Seq(tx1, tx2, tx3)) ++ Stream
        .eval(edgeFactory.ready(tx1))
        .flatMap(
          _ =>
            Stream.emits(Seq(tx4, tx5)) ++ Stream
              .eval(edgeFactory.ready(tx4))
              .map(_ => tx6)
        )
    }

    val pipeline: Stream[IO, Unit] = for {
      (nodeA, nodeB, nodeC) <- cluster

      s <- Stream.eval(Semaphore[IO](maxRoundsInProgress))
      txs <- runPredefinedScenario
        .through(edgeFactory.createEdges)
        .map(L1Cell)
        .map { l1cell => // from cache
          Stream.eval {
            s.tryAcquire.ifM(
              IO(Log.green(s"[nodeA][ConsensusStart] ${l1cell}")) >> nodeA
                .startL1Consensus(l1cell)
                .guarantee(s.release),
              IO {
                println(s"store txs = ${l1cell.edge.txs}")
                L1Block(Set.empty).asRight[CellError] // TODO: ???
              }
            )
          }
        }
        .parJoin(parallelJobs)
        .flatTap(
          block =>
            Stream.eval {
              IO {
                Log.magenta(block)
              }
            }
        )
    } yield ()

    pipeline.compile.drain.as(ExitCode.Success)
  }
}

object RandomDemo extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val generateTxEvery = 0.1.seconds
    val nTxs = 20
    val maxRoundsInProgress = 2
    val parallelJobs = 3
    val edgeFactory = L1EdgeFactory("nodeA")

    val cluster: Stream[IO, (Node, Node, Node)] = Stream.eval {
      for {
        nodeA <- Node.run("nodeA", "A")
        nodeB <- Node.run("nodeB", "B")
        nodeC <- Node.run("nodeC", "C")

        _ <- nodeA.joinTo(Set(nodeB, nodeC))
        _ <- nodeB.joinTo(Set(nodeA, nodeC))
        _ <- nodeC.joinTo(Set(nodeA, nodeB))
      } yield (nodeA, nodeB, nodeC)
    }

    def transactions(txGenerator: RandomTransactionGenerator): Stream[IO, L1Transaction] =
      Stream
        .repeatEval(txGenerator.generateRandomTransaction())
        .metered(generateTxEvery)
        .take(nTxs)

    val pipeline: Stream[IO, Unit] = for {
      (nodeA, nodeB, nodeC) <- cluster

      s <- Stream.eval(Semaphore[IO](maxRoundsInProgress))
      txs <- transactions(nodeA.txGenerator) // We simulate transactions coming to nodeA from address A
        .through(edgeFactory.createEdges)
        .map(L1Cell)
        .map { l1cell =>
          Stream.eval {
            s.tryAcquire.ifM(
              IO(Log.green(s"[nodeA][ConsensusStart] ${l1cell}")) >> nodeA
                .startL1Consensus(l1cell)
                .guarantee(s.release)
                .flatTap {
                  case Right(L1Block(txs)) => txs.toList.traverse(edgeFactory.ready)
                  case _                   => IO.unit
                },
              IO {
                println(s"store txs = ${l1cell.edge.txs}")
                L1Block(Set.empty).asRight[CellError] // TODO: ???
              }
            )

          }
        }
        .parJoin(parallelJobs)
        .flatTap(block => Stream.eval { IO { Log.magenta(block) } })

      //      _ <- Stream.eval { IO { println(txs) } }
    } yield ()

    pipeline.compile.drain.as(ExitCode.Success)
  }
}

object StreamBlocksDemo extends IOApp {

  val tips: Stream[IO, Int] = Stream
    .range[IO](1, 1000)
    .metered(2.second)

  val blocks: Stream[IO, L1Block] = Stream
    .range[IO](1, 100)
    .map(a => L1Transaction(a, "A", "B", "", a))
    .map(Set(_))
    .map(L1Block)
    .metered(1.second)

  type Height = Int
  type HeightsMap = Map[Height, Set[L1Block]]
  val tipInterval = 2
  val tipDelay = 5

  val edges = blocks.map(_.asRight[Int]).merge(tips.map(_.asLeft[L1Block])).through {
    def go(s: Stream[IO, Either[Int, L1Block]], state: (HeightsMap, Int, Int)): Pull[IO, L0Edge, Unit] =
      state match {
        case (heights, lastTip, lastEmitted) =>
          s.pull.uncons1.flatMap {
            case None => Pull.done
            case Some((Left(tip), tail)) if tip - tipDelay >= lastEmitted + tipInterval =>
              val range = ((lastEmitted + 1) to (lastEmitted + tipInterval))
              val blocks = range.flatMap(heights.get).flatten.toSet

              Log.yellow(
                s"Triggering snapshot at range: ${range} | Blocks: ${blocks.size} | Heights aggregated: ${heights.removedAll(range).keySet.size}"
              )

              Pull.output1(L0Edge(blocks)) >> go(tail, (heights.removedAll(range), tip, lastEmitted + tipInterval))
            case Some((Left(tip), tail)) => go(tail, (heights, tip, lastEmitted))
            case Some((Right(block), tail)) =>
              go(
                tail,
                (
                  heights.updatedWith(block.height)(_.map(_ ++ Set(block)).orElse(Set(block).some)),
                  lastTip,
                  lastEmitted
                )
              )
          }
      }

    in => go(in, (Map.empty, 0, 0)).stream
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val pipeline = for {
      edge <- edges
      cell = L0Cell(edge)
      result <- Stream.eval { cell.run() }
      _ <- Stream.eval { IO { Log.red(result) } }
    } yield result

    val l0 = pipeline
      .mapFilter(_.toOption) // pipeline returns Either[CellError, Ω] but we want to broadcast correct snapshots only
      .broadcastThrough(
        (in: Stream[IO, Ω]) =>
          in.flatMap { snapshot =>
            Stream.eval(IO(Log.cyan(s"Acceptance: ${snapshot}")))
          },
        (in: Stream[IO, Ω]) =>
          in.flatMap { snapshot =>
            Stream.eval(IO(Log.green(s"Majority state chooser: ${snapshot}")))
          }
        // and other subscribers...
      )

    l0.compile.drain.as(ExitCode.Success)
  }
}
