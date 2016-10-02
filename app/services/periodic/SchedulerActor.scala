package services.periodic

import javax.inject.Inject

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import services.OAuth2Service.AccessToken
import services.SocialServiceConnectors

import scala.concurrent.{ExecutionContext, Future}

object SchedulerActor {

  sealed trait Request
  object Request {
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

  implicit val ec: ExecutionContext = context.dispatcher

  override def preStart(): Unit = {
    logger.info("Pre start")
  }

  override def receive: Receive = {
    case Request.UpdateNetwork =>
      dataAccessManager.findAllUsers() flatMap { users =>
        for {
          userAndPersons <- Future.sequence(users map { user =>
            dataAccessManager.findPersonsByUserId(user.id) map { persons =>
              (user, persons)
            }
          })

          personWithConnectors <- Future(userAndPersons flatMap { case (user, persons) =>
            persons map { person =>
              (user, person, socialServiceConnectors.provideByAppId(person.entity.internalId.serviceType))
            }
          })

          friendsList <- Future.sequence(personWithConnectors map {
            case (user, person, Some(connector)) =>
              for {
                sessions <- dataAccessManager.findSessionsByUserId(user.id)
                latestSession <- Future(sessions.sortBy(_.entity.created.toEpochSecond).head)
                friends <- connector.requestFriendsList(AccessToken(latestSession.entity.accessToken), person.entity.internalId)
              } yield (person, friends)
          })

          relations <- Future.sequence(friendsList map { case (person, friends) =>
            Future.sequence(friends map { friend =>
              dataAccessManager.updateOrCreatePerson(friend) flatMap { friendId =>
                dataAccessManager.linkRelation(person.id, friendId)
              }
            })
          })
        } yield println(s"${relations.flatten.size} nodes updated/created")
      }
    case e: Any =>
      logger.error(s"Unknown request received by SchedulerActor: $e")
  }

}
