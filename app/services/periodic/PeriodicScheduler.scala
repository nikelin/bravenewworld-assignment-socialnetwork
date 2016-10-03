package services.periodic

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import javax.inject.Singleton

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class PeriodicScheduler @Inject() (system: ActorSystem, @Named("scheduler-actor") val schedulerActor: ActorRef)
  extends LazyLogging {

  implicit val ec = system.dispatcher

  system.scheduler.schedule(0.microseconds, 5.seconds, schedulerActor, SchedulerActor.Request.UpdateNetwork)

  // update persons interests

  // update persons work experience

  // update persons social activity (posts)

  // update person posts stats (likes count, shares count, etc.)
}
