package services.periodic

import java.time.Instant
import javax.inject.Inject

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, OneForOneStrategy}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import models.{Id, MaterializedEntity, Person}
import services.oauth.SocialServiceConnectors
import utils._

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object SchedulerActor {

  private[SchedulerActor] sealed trait RequestPrivate
  private[SchedulerActor] object RequestPrivate {
    case object InvalidateActive extends RequestPrivate
    case object ExecuteNetworkUpdates extends RequestPrivate
    case class SchedulePersonUpdate(person: MaterializedEntity[Person], level: Int) extends Request
    case class ScheduleRelationsUpdate(person: MaterializedEntity[Person], level: Int) extends Request
  }

  sealed trait Request
  object Request {
    case object UpdateNetwork extends Request
    case object UpdateProfile extends Request
    case object UpdateWorkExperience extends Request
    case object UpdateInterests extends Request
    case object UpdateSocialActivity extends Request
    case class RequestPositionInQueue(person: Id[Person]) extends Request
    case class RequestPositionsInQueue(person: Seq[Id[Person]]) extends Request
  }

  sealed trait PositionInQueue
  object PositionInQueue {
    case object InActiveQueue extends PositionInQueue
    case class ScheduledAt(position: Int) extends PositionInQueue
    case class ProcessedAt(time: Instant) extends PositionInQueue
    case object NotScheduled extends PositionInQueue
  }

  sealed trait Response
  object Response {
    case class PersonPositionInQueue(position: PositionInQueue)
    case class PersonsPositionInQueue(position: Map[Id[Person], PositionInQueue])
  }

  case class PrioritizedPerson(person: MaterializedEntity[Person], level: Int)

  implicit val prioritizedPersonOrdering: Ordering[PrioritizedPerson] = Ordering.fromLessThan((l, r) => l.level > r.level)

}

class SchedulerActor @Inject() (config: Config, socialServiceConnectors: SocialServiceConnectors,
                                dataAccessManager: DataAccessManager)
  extends Actor with LazyLogging {
  import SchedulerActor._

  implicit val ec: ExecutionContext = context.system.dispatchers.lookup("scheduler-dispatcher")

  private final val queue = mutable.PriorityQueue[PrioritizedPerson]()
  private final val active = mutable.HashSet[Id[Person]]()
  private final val activeMap = mutable.HashMap[Id[Person], Instant]()
  private final val processed = mutable.HashMap[Id[Person], Instant]()

  private final val schedulerStackSize = config.getInt("scheduler.stackSize")
  private final val schedulerTimeout = config.getDuration("scheduler.processingTimeout")
  private final val schedulerRefreshTime = config.getDuration("scheduler.refreshTime")

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 25, withinTimeRange = 10.minute) {
      case e: Throwable => Restart
    }

  override def preStart(): Unit = {
    context.system.scheduler.schedule(0.seconds, 1.seconds, self, RequestPrivate.ExecuteNetworkUpdates)
    context.system.scheduler.schedule(0.seconds, 1.seconds, self, Request.UpdateNetwork)
    context.system.scheduler.schedule(0.seconds, 1.seconds, self, RequestPrivate.InvalidateActive)
  }

  override def postStop(): Unit = {
    logger.info("Post stop")
  }

  private def computePosition(person: Id[Person]): SchedulerActor.PositionInQueue = {
    if ( active.contains(person) ) PositionInQueue.InActiveQueue
    else {
      queue.zipWithIndex.find(_._1.person.id == person) match {
        case Some(scheduledPair) ⇒ PositionInQueue.ScheduledAt(scheduledPair._2)
        case None ⇒
          processed.zipWithIndex.find(_._1._1 == person) match {
            case Some(processedPair) ⇒ PositionInQueue.ProcessedAt(processedPair._1._2)
            case None ⇒ PositionInQueue.NotScheduled
          }
      }
    }
  }

  override def receive: Receive = {
    case Request.RequestPositionsInQueue(persons) ⇒
      "positions queue" timing {
        sender() ! Response.PersonsPositionInQueue((persons map { person ⇒
          (person, computePosition(person))
        }).toMap)
      }

    case Request.RequestPositionInQueue(person) ⇒
      sender() ! Response.PersonPositionInQueue(computePosition(person))

    case RequestPrivate.InvalidateActive if active.nonEmpty ⇒
      "active entries invalidation" timing {
        val items = active filter { entry ⇒
          activeMap.get(entry)
            .exists { time ⇒
              Instant.now().minusMillis(time.toEpochMilli).toEpochMilli > schedulerTimeout.toMillis
            }
        } map { expired ⇒
          active -= expired
          activeMap -= expired
          processed += expired → Instant.now
          expired
        }

        logger.info(s"$items evicted from the active queue")
      }

    case RequestPrivate.ExecuteNetworkUpdates =>
      logger.info(s"Executing update, " +
        s"active=${active.size}; " +
        s"queue=${queue.size}; " +
        s"processed=${processed.size}; ")

      if (queue.nonEmpty && active.size < schedulerStackSize) {
        "execute network updates" timing Future {
          val record = queue.dequeue()

          active += record.person.id
          activeMap += record.person.id → Instant.now

          val connector = socialServiceConnectors.provideByAppId(record.person.entity.internalId.serviceType)
            .get

          Future {
            Await.ready(
              (for {
                friendsList <- connector.requestFriendsList(None, record.person.entity.internalId) flatMap (result =>
                  Future.sequence(result map { friend =>
                    dataAccessManager.findPersonByInternalId(friend.person.internalId) flatMap {
                      case Some(friendRecord) ⇒
                        dataAccessManager.linkRelation(record.person.id, friendRecord.id)
                      case None ⇒
                        Future(logger.info("Corrupted relation returned"))
                    }
                  })
                  )
              } yield {
                logger.info(s"${friendsList.size} nodes updated/created")
              }) recover {
                case e if NonFatal(e) =>
                  logger.error("UpdateNetwork request failed", e)
              },
              3.minutes
            )
          } onComplete { e ⇒
            active -= record.person.id
            e match {
              case Success(_) ⇒
                logger.info("UpdateNetwork complete")
                processed.put(record.person.id, Instant.now())

              case Failure(e) ⇒ logger.error("UpdateNetwork failed", e)
            }
          }
        }
      } else if (queue.isEmpty) {
        logger.info("Unable to schedule new tasks - tasks queue is empty")
      } else {
        logger.info("Unable to schedule new tasks - no slot available in stack")
      }

    case RequestPrivate.ScheduleRelationsUpdate(person, level) =>
      "relations update" timing {
        dataAccessManager.findRelationsByPersonId(person.id) map { persons =>
          persons foreach { person =>
            self ! RequestPrivate.SchedulePersonUpdate(person, level + 1)
          }
        }
      }

    case RequestPrivate.SchedulePersonUpdate(person, level) =>
      val isScheduled = queue.exists(_.person.id == person.id)
      val isActive = active.contains(person.id)
      val isRecentlyUpdated = processed.find(_._1.value == person.id.value).exists { case (_, time) =>
        Instant.now.toEpochMilli - time.toEpochMilli < schedulerRefreshTime.toMillis
      }

      if (!isScheduled && !isActive && !isRecentlyUpdated) {
        queue += PrioritizedPerson(person, level)
      }

    case Request.UpdateNetwork =>
      dataAccessManager.findAllUsers() flatMap { users =>
        Future.sequence(
          users map { user =>
            dataAccessManager.findPersonsByUserId(user.id) map { persons =>
              persons foreach { person =>
                val level = if (person.entity.isIdentity) 0 else 1

                self ! RequestPrivate.ScheduleRelationsUpdate(person, level)
                self ! RequestPrivate.SchedulePersonUpdate(person, level)
              }
            }
          }
        )
      }

    case Request.UpdateInterests =>
      dataAccessManager.findAllUsers() flatMap { users =>
        Future.sequence(
          users map { user =>
            dataAccessManager.findPersonsByUserId(user.id) flatMap { persons =>
              Future.sequence(persons map { person =>
                val connector = socialServiceConnectors.provideByAppId(person.entity.internalId.serviceType).get
                connector.requestInterestsList(None, person.id) flatMap { interests =>
                  dataAccessManager.updatePersonAttributes(person.id, interests.toSeq) map { attributes =>
                    logger.info("Person interests has been updated")
                  }
                }
              })
            }
          }
        )
      }

    case e: Request =>
    //      logger.info(s"Unsupported or unacceptable request received $e")
    case e: RequestPrivate =>
    //      logger.info(s"Unsupported or unacceptable request received $e")
    case e: Any => logger.error(s"Unknown message received $e")
  }

}
