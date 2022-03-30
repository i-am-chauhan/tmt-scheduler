package com.tmt.scheduler

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


class TimeScheduler {

  private var stop:Boolean = false
  private def nextInterval(starTime: Instant) = {
    val currentTime = Instant.now()
    if (starTime.compareTo(currentTime) >= 0) {
      Duration.between(currentTime, starTime)
    } else Duration.ofMillis(0)
  }

  private def scheduleWithin(intervalInMillis: Int)(task: Runnable): Unit = {
    println("hello I'm here")
    while (!stop) {
      task.run()
      val startTime = Instant.now().plus(intervalInMillis, ChronoUnit.MILLIS)
      val interval = nextInterval(startTime)
      val nano = interval.getNano
      val millisInterval = interval.getSeconds * 1000 + (nano / (1000 * 1000))
      Thread.sleep(millisInterval, nano % (1000 * 1000))
    }
  }

  def schedule(afterInMillis: Int, intervalInMillis: Int)(task: => Unit) = {
    val unit: Runnable = () => {
      println("hello")
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

  scheduler.schedule(10, 1) {
    times.append(Instant.now())
  }

  Thread.sleep(1000)
  scheduler.cancel()
  println(times.size)
}
