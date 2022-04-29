package com.tmt.scheduler

import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.locks.LockSupport
import scala.collection.mutable
import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future
}

trait Cancellable {
  def cancel(): Unit
}

case class ScanTask(runnable: () => Any, interval: Int) {
  private var _waitTicks: Int = 0

  def waitTicks: Int = synchronized { _waitTicks }
  def decWaitTicks(): Unit = synchronized { _waitTicks -= 1 }
  def resetWaitTicks(): Unit = synchronized { _waitTicks = interval }
}

class TimeScheduler()(implicit ec: ExecutionContextExecutorService) {

  private var stop: Boolean = false
  private var tasks: Map[UUID, ScanTask] = Map.empty

  private val thread = new Thread(() => start())
  thread.start()

  def shutdown(): Unit = synchronized {
    stop = true
    ec.shutdown()
  }

  private def registerTask(
      task: () => Unit,
      interval: Int
  ): Cancellable = synchronized {
    val id: UUID = UUID.randomUUID()
    val scanTask = ScanTask(task, interval)
    tasks = tasks + (id -> scanTask)
    () => synchronized { tasks = tasks - id }
  }

  private def start(): Unit = {
    while (!stop) {
      val it = tasks.iterator
      while (it.hasNext) {
        val task = it.next()._2
        if (task.waitTicks == 0) {
          Future { task.runnable() }
          task.resetWaitTicks()
        }
        task.decWaitTicks()
      }
      tick()
    }
  }

  private def tick(): Unit = {
    val oneMilliInNanos = 1000 * 1000
    val sleepIntervalInNano =
      oneMilliInNanos - Instant.now().getNano % oneMilliInNanos
    LockSupport.parkNanos(sleepIntervalInNano)
  }

  def schedule(intervalInMillis: Int)(task: () => Unit): Cancellable = {
    registerTask(task, intervalInMillis)
  }
}

object Main extends App {

  private implicit val executorService: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
  private val scheduler = new TimeScheduler()

  var readings = mutable.Map.empty[String, mutable.ArrayBuffer[Long]]

  List(
    ("1-ms-task-A", 1),
    ("1-ms-task-B", 1),
    ("10-ms-task-A", 10),
    ("10-ms-task-B", 10),
    ("100-ms-task-A", 100),
    ("100-ms-task-B", 100),
    ("1000-ms-task-A", 1000),
    ("1000-ms-task-B", 1000)
  ).foreach { case (str, i) =>
    val jittersInMicros = mutable.ArrayBuffer.empty[Long]
    var startTime = Instant.now()
    readings.update(str, jittersInMicros)
    scheduler.schedule(i) { () =>
      synchronized {
        val diff = Duration.between(startTime, Instant.now()).abs().toNanos
        jittersInMicros += Math.round(diff.toDouble / 1000)
        startTime = Instant.now().plusNanos(i * 1000 * 1000)
      }
    }
  }

  Thread.sleep(60000)
  scheduler.shutdown()

  readings.foreach { case (str, value) =>
    val jitterInMicros = value.sum / value.length
    println(
      s"$str : jitter = $jitterInMicros microsecs (${jitterInMicros.toDouble / 1000} ms)"
    )
  }
}
