package services.periodic

import javax.inject.Inject

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import services.OAuth2Service.AccessToken
import services.SocialServiceConnectors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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

  override def postStop(): Unit = {
    logger.info("Post stop")
  }

  override def preStart(): Unit = {
    logger.info("Pre start")
  }

  override def receive: Receive = {
    case Request.UpdateNetwork =>
      dataAccessManager.findAllUsers() flatMap { users =>
        println(s"Updating ${users.length} users")

        for {
          userAndPersons <- Future.sequence(users map { user =>
            dataAccessManager.findPersonsByUserId(user.id) map { persons =>
              println(s"${persons.length} persons resolved for user")
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
                _ = println(s"Session ${sessions} resolved for user")
                latestSession <- Future(sessions.sortBy(_.entity.created.toEpochSecond).headOption)
                friends <- if(latestSession.nonEmpty)
                  connector.requestFriendsList(AccessToken(latestSession.head.entity.accessToken), person.entity.internalId)
                  else Future(Seq())
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
      } recover {
        case e if NonFatal(e) =>
          e.printStackTrace()
      }
    case e: Any =>
      logger.error(s"Unknown request received by SchedulerActor: $e")
  }

}
