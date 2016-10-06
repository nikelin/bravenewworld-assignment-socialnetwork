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

  implicit val ec = ExecutionContext.Implicits.global

  system.scheduler.schedule(10.seconds, 10.second, schedulerActor, SchedulerActor.Request.UpdateNetwork)
}
