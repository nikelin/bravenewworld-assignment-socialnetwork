package controllers

import javax.inject.Inject

import com.typesafe.config.Config
import dal.DataAccessManager
import models.ServiceType
import play.api.mvc.{Action, AnyContent, Controller}
import services.RelationshipValueEstimator
import services.oauth.OAuth2Service

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class SocialRelationsController @Inject() (dataAccessManager: DataAccessManager,
                                           relationshipValueEstimator: RelationshipValueEstimator,
                                           oAuth2Service: OAuth2Service)
  extends Controller {

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext

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
            .map ( row => (relations.find(_.id == row._1).get, relationAttributes.find(_._1 == row._1).map(_._2).get, row._2))
            .sortWith  ( _._3.totalValue > _._3.totalValue)
      } yield {
        Ok(
          views.html.social.main(user, applicationId, person, personAttributes, materializedEstimatedRelations)
        )
      }

      future recover {
        case e if NonFatal(e) =>
          Ok(views.html.error(e.getMessage))
      }
    }

}
