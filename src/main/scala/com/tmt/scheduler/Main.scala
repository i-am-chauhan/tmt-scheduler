package com.tmt.scheduler

import java.time
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.locks.LockSupport
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

trait Cancellable {
  def cancel(): Unit
}

case class ScanTask(runnable: () => Future[Any], interval: Int) {
  private var _waitTicks: Int = 0

  def waitTicks: Int =  { _waitTicks }
  def decWaitTicks(): Unit =  { _waitTicks -= 1 }
  def resetWaitTicks(): Unit =  { _waitTicks = interval }
}

class TimeScheduler {

  private var stop: Boolean = false
  private var tasks: Map[UUID, ScanTask] = Map.empty

  private val thread = new Thread(() => start())
  thread.start()

  def shutdown(): Unit =  { stop = true }

  private def registerTask(
      task: () => Future[Unit],
      interval: Int
  ): Cancellable =  {
    val id: UUID = UUID.randomUUID()
    val scanTask = ScanTask(task, interval)
    tasks = tasks + (id -> scanTask)
    () => { tasks = tasks - id }
  }

  private def start(): Unit = {
    while (!stop) {
      val it = tasks.iterator
      while (it.hasNext) {
        val task = it.next()._2
        if (task.waitTicks == 0) {
          task.runnable()
          task.resetWaitTicks()
        }
        task.decWaitTicks()
      }
      tick()
    }
  }

  private def tick(): Unit = {

    val oneMilliInNanos = 1000 * 1000
//    val sleepIntervalInNano = oneMilliInNanos - System.nanoTime() % oneMilliInNanos
    val sleepIntervalInNano = oneMilliInNanos - Instant.now().getNano % oneMilliInNanos
    LockSupport.parkNanos(sleepIntervalInNano)
//    val startTime = System.nanoTime()
//    while (startTime + oneMilliInNanos >= System.nanoTime()){}
  }

  def schedule(intervalInMillis: Int)(task: () => Future[Unit]): Cancellable = {
    registerTask(task, intervalInMillis)
  }
}

object Main extends App {

  private val times10ms: ArrayBuffer[Instant] = mutable.ArrayBuffer.empty
  private val times100ms: ArrayBuffer[Instant] = mutable.ArrayBuffer.empty

  private val scheduler = new TimeScheduler

  private implicit val executorService: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

//  (0 to 20).map(x =>
//    scheduler.schedule(10) { () =>
//      Future {
//        println(s"Hello $x")
//      }
//    }
//  )

  var count = 0
  scheduler.schedule(10)(() => {
    Future.successful {
        if (count > 60) times10ms.append(Instant.now())
        count += 1
    }
  })

  Thread.sleep(30000)
//  a.cancel()
//  b.cancel()
  scheduler.shutdown()
  executorService.shutdown()
//  println("*************************")
//  println(count)
  times10ms.reduce((prev, curr) => {
    val nanos = time.Duration.between(prev, curr).toNanos
    println("jitter in nanos " + nanos)
    curr
  })
//  println(
//    times100ms.map(x => s"${x.getEpochSecond} ${x.getNano}").mkString("\n")
//  )
}
