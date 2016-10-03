package controllers

import javax.inject.Inject

import com.typesafe.config.Config
import dal.DataAccessManager
import models.ServiceType
import play.api.mvc.{Action, AnyContent, Controller}
import services.{OAuth2Service, RelationshipValueEstimator}

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
        relations <- dataAccessManager.findRelationsByPersonId(person.id)
        estimatedRelations <- relationshipValueEstimator.process(person.id, relations.map(_.id))
        applicationId = oAuth2Service.oauthAuthId(person.entity.internalId.serviceType.asString)
        materializedEstimatedRelations = estimatedRelations map ( row => row.copy(_1 = relations.find(_.id == row._1).get)) sortWith  ( _._2.totalValue > _._2.totalValue)
      } yield {
        Ok(
          views.html.social.main(user, applicationId, person, materializedEstimatedRelations)
        )
      }

      future recover {
        case e if NonFatal(e) =>
          Ok(views.html.error(e.getMessage))
      }
    }

}
