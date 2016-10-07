package services.periodic

import java.time.Instant
import javax.inject.Inject

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import models.{Id, MaterializedEntity, Person}
import services.oauth.SocialServiceConnectors

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

object SchedulerActor {

  private[SchedulerActor] sealed trait RequestPrivate
  private[SchedulerActor] object RequestPrivate {
    case object ExecuteUpdates extends RequestPrivate
    case class SchedulePersonUpdate(person: MaterializedEntity[Person], level: Int) extends Request
    case class ScheduleRelationsUpdate(person: MaterializedEntity[Person]) extends Request
  }

  sealed trait Request
  object Request {
    case object UpdateNetwork extends Request
    case object UpdateProfile extends Request
    case object UpdateWorkExperience extends Request
    case object UpdateInterests extends Request
    case object UpdateSocialActivity extends Request
  }

  case class PrioritizedPerson(person: MaterializedEntity[Person], level: Int)

  implicit val prioritizedPersonOrdering: Ordering[PrioritizedPerson] = Ordering.fromLessThan(_.level < _.level)

}

class SchedulerActor @Inject() (socialServiceConnectors: SocialServiceConnectors,
                     dataAccessManager: DataAccessManager)
  extends Actor with LazyLogging {
  import SchedulerActor._

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private final val queue = mutable.PriorityQueue[PrioritizedPerson]()
  private final val active = mutable.HashSet[Id[Person]]()
  private final val processed = mutable.HashMap[Id[Person], Instant]()

  override def receive: Receive = {
    case RequestPrivate.ExecuteUpdates if queue.nonEmpty && active.size < 5 =>
      val record = queue.dequeue()
      active += record.person.id

      val connector = socialServiceConnectors.provideByAppId(record.person.entity.internalId.serviceType)
        .get

      (for {
        friendsList <- connector.requestFriendsList(None, record.person.entity.internalId) flatMap (result =>
          Future.sequence(result map { friend =>
            dataAccessManager.updateOrCreatePerson(friend.person) flatMap { friendId =>
              logger.info("Person created")
              dataAccessManager.updatePersonAttributes(friendId, friend.attribute) flatMap { _ =>
                logger.info("Person attributes updated")
                dataAccessManager.linkRelation(record.person.id, friendId) map { _ =>
                  logger.info("Relations updated")
                }
              }
            }
          })
        )
      } yield {
        logger.info(s"${friendsList.size} nodes updated/created")
        active -= record.person.id
        processed.put(record.person.id, Instant.now())
      }) recover {
        case e if NonFatal(e) =>
          logger.error("UpdateNetwork request failed", e)
          active -= record.person.id
          processed.put(record.person.id, Instant.now())
      }

    case RequestPrivate.ScheduleRelationsUpdate(person) =>
      dataAccessManager.findRelationsByPersonId(person.id) map { persons =>
        persons foreach { person =>
          self ! RequestPrivate.SchedulePersonUpdate(person, 2)
        }
      }

    case RequestPrivate.SchedulePersonUpdate(person, level) =>
      val isScheduled = queue.exists(_.person.id.value == person.id.value)
      val isActive = active.exists(_.value == person.id.value)
      val isRecentlyUpdated = processed.get(person.id).exists(time =>
        Instant.now.toEpochMilli - time.toEpochMilli > 5.minutes.toMillis
      )

      if (!isScheduled && !isActive && !isRecentlyUpdated) {
        queue += PrioritizedPerson(person, 1)
      }

    case Request.UpdateNetwork =>
      dataAccessManager.findAllUsers() flatMap { users =>
        Future.sequence(
          users map { user =>
            dataAccessManager.findPersonsByUserId(user.id) map { persons =>
              persons foreach { person =>
                dataAccessManager.computePersonLevel(person.id) map { level =>
                  self ! RequestPrivate.ScheduleRelationsUpdate(person)
                  self ! RequestPrivate.SchedulePersonUpdate(person, 1)
                }
              }
            }
          }
        )
      }
    case _: Request =>
    case _: RequestPrivate =>
    case e: Any => logger.error(s"Unknown message received $e")
  }

  context.system.scheduler.schedule(0.seconds, 1.seconds, self, RequestPrivate.ExecuteUpdates)

}
