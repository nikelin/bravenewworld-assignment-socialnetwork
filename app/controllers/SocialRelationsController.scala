package controllers

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import dal.DataAccessManager
import models._
import play.api.mvc.{Action, AnyContent, Controller}
import services.RelationshipValueEstimator
import services.oauth.OAuth2Service
import services.periodic.SchedulerActor

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

object SocialRelationsController {
  case class RelationData(
      person: MaterializedEntity[Person],
      attributes: Seq[PersonAttribute],
      score: RelationshipValueEstimator.Score,
      relations: Seq[Id[Person]],
      positionInQueue: SchedulerActor.PositionInQueue)
}

class SocialRelationsController @Inject() (dataAccessManager: DataAccessManager,
                                           relationshipValueEstimator: RelationshipValueEstimator,
                                           oAuth2Service: OAuth2Service,
                                           @Named("scheduler-actor") schedulerActorRef: ActorRef)
  extends Controller {

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext

  implicit val timeout: Timeout = Timeout(20.second)

  def home: Action[AnyContent] =
    IsAuthenticated(dataAccessManager) { (request, user) =>
      val future = for {
        persons <- dataAccessManager.findPersonsByUserId(user.id)
        person = persons.head
        personAttributes <- dataAccessManager.findPersonAttributesByPersonId(person.id)
        relations <- dataAccessManager.findRelationsByPersonId(person.id)
        estimatedRelations <- relationshipValueEstimator.process(person.id, relations.map(_.id))
        relationAttributes <- Future.sequence(estimatedRelations map { rel => dataAccessManager.findPersonAttributesByPersonId(rel._1) map { attrs => (rel._1, attrs)}})
        applicationId = oAuth2Service.oauthAuthId(person.entity.internalId.serviceType.asString)
        materializedEstimatedRelations =
          estimatedRelations
            .map ( row =>
              (
                relations.find(_.id == row._1).get,
                relationAttributes.find(_._1 == row._1).map(_._2).get,
                row._2,
                row._3
              )
            )
            .sortWith  ( _._3.totalValue > _._3.totalValue)
        estimatedRelationsWithPriority ←
          (schedulerActorRef ? SchedulerActor.Request.RequestPositionsInQueue(materializedEstimatedRelations map (_._1.id)))
            .map {
              case SchedulerActor.Response.PersonsPositionInQueue(map) ⇒
                materializedEstimatedRelations map { rel ⇒
                  SocialRelationsController.RelationData(rel._1, rel._2, rel._3, rel._4,
                    map.getOrElse(rel._1.id, SchedulerActor.PositionInQueue.NotScheduled))
                }
              case _ ⇒ throw new IllegalStateException()
            }
        placeInQueue ← schedulerActorRef.ask(SchedulerActor.Request.RequestPositionInQueue(person.id)) map {
          case SchedulerActor.Response.PersonPositionInQueue(p) ⇒ p
          case _ ⇒ SchedulerActor.PositionInQueue.NotScheduled
        }
      } yield {
        Ok(
          views.html.social.main(user, applicationId, person,
            personAttributes, estimatedRelationsWithPriority, placeInQueue)
        )
      }

      future recover {
        case e if NonFatal(e) =>
          Ok(views.html.error(e.getMessage))
      }
    }

}
