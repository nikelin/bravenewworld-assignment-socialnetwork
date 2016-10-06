package services.periodic

import java.time.Instant
import javax.inject.Inject

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import models.{Id, Person}
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector.PersonWithAttributes
import services.oauth.SocialServiceConnectors
import services.periodic.SchedulerActor.Request.SchedulePersonUpdate

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

object SchedulerActor {

  private[SchedulerActor] sealed trait RequestPrivate
  private[SchedulerActor] object RequestPrivate {
    case object ExecuteUpdates extends RequestPrivate
  }

  sealed trait Request
  object Request {
    case class SchedulePersonUpdate(person: Id[Person]) extends Request
    case object UpdateNetwork extends Request
    case object UpdateProfile extends Request
    case object UpdateWorkExperience extends Request
    case object UpdateInterests extends Request
    case object UpdateSocialActivity extends Request
  }

}

class SchedulerActor @Inject() (socialServiceConnectors: SocialServiceConnectors,
                     dataAccessManager: DataAccessManager)
  extends Actor with LazyLogging {
  import SchedulerActor._

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private final val queue = mutable.Queue[Id[Person]]()
  private final val active = mutable.HashSet[Id[Person]]()
  private final val processed = mutable.HashMap[Id[Person], Instant]()

  override def receive: Receive = {
    case RequestPrivate.ExecuteUpdates if queue.nonEmpty && active.size < 5 =>
      val record = queue.dequeue()
      active += record

      (for {
        personOpt <- dataAccessManager.findPersonById(record)
        if personOpt.nonEmpty
        person = personOpt.get
        connectorOpt = socialServiceConnectors.provideByAppId(person.entity.internalId.serviceType)
        if connectorOpt.nonEmpty
        connector = connectorOpt.get
        friendsList <- connector.requestFriendsList(None, person.entity.internalId) flatMap (result =>
          Future.sequence(result map { friend =>
            dataAccessManager.updateOrCreatePerson(friend.person) flatMap { friendId =>
              logger.info("Person created")
              dataAccessManager.updatePersonAttributes(friendId, friend.attribute) flatMap { _ =>
                logger.info("Person attributes updated")
                dataAccessManager.linkRelation(person.id, friendId) map { _ =>
                  logger.info("Relations updated")
                }
              }
            }
          })
        )
      } yield {
        logger.info(s"${friendsList.size} nodes updated/created")
        active -= record
        processed.put(record, Instant.now())
      }) recover {
        case e if NonFatal(e) =>
          logger.error("UpdateNetwork request failed", e)
          active -= record
          processed.put(record, Instant.now())
      }

    case Request.SchedulePersonUpdate(person) =>
      val isScheduled = queue.exists(_.value == person.value)
      val isActive = active.exists(_.value == person.value)
      val isRecentlyUpdated = processed.get(person).exists(time =>
        Instant.now.toEpochMilli - time.toEpochMilli > 5.minutes.toMillis
      )

      if (!isScheduled && !isActive && !isRecentlyUpdated) {
        queue += person
      }

    case Request.UpdateNetwork =>
      dataAccessManager.findAllPersons() map { persons =>
        persons foreach { person =>
          self ! Request.SchedulePersonUpdate(person.id)
        }
      }
    case e: Any =>
  }

  context.system.scheduler.schedule(0.seconds, 5.seconds, self, RequestPrivate.ExecuteUpdates)

}
