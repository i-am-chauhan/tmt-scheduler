package com.tmt.scheduler

import java.time.Instant
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TimeScheduler {

  private var stop: Boolean = false
//  private def nextInterval(starTime: Instant) = {
//    val currentTime = Instant.now()
//    if (starTime.compareTo(currentTime) >= 0) {
//      Duration.between(currentTime, starTime)
//    } else Duration.ofMillis(0)
//  }

  private def scheduleWithin(intervalInMillis: Int)(task: Runnable): Unit = {
    var waitTicks = 0

    while (!stop) {
      if (waitTicks == 0) {
        task.run()
        waitTicks = intervalInMillis
      }
      val sleepIntervalInNano = 1000 * 1000 - Instant.now().getNano % 1000
//      println(sleepInterval)
      Thread.sleep(sleepIntervalInNano / (1000 * 1000), sleepIntervalInNano % (1000 * 1000))
      waitTicks -= 1
    }
  }

  def schedule(afterInMillis: Int, intervalInMillis: Int)(task: => Unit) = {
    val unit: Runnable = () => {
      Thread.sleep(afterInMillis)
      scheduleWithin(intervalInMillis)(() => task)
    }
    val t = new Thread(unit)
    t.start()

  }

  def cancel(): Unit = stop = true
}

object Main extends App {

  private val scheduler = new TimeScheduler

  private val times: ArrayBuffer[Instant] = mutable.ArrayBuffer.empty

  scheduler.schedule(10, 10) {
    times.append(Instant.now())
  }

  Thread.sleep(10000)
  scheduler.cancel()
  println(times.map(x => s"${x.getEpochSecond} ${x.getNano}").mkString("\n"))
}
