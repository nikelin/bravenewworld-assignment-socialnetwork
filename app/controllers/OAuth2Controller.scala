package controllers

import java.time.ZonedDateTime
import javax.inject.Inject

import dal.DataAccessManager
import models.{ServiceType, User}
import play.api.mvc.{Action, AnyContent, Controller}
import services.{OAuth2Service, SocialServiceConnectors}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2Controller @Inject()(oauthService: OAuth2Service,
                                 socialServiceConnectors: SocialServiceConnectors,
                                 dataAccessManager: DataAccessManager)
  extends Controller {

  implicit val ec: ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext

  def authenticate(appId: String): Action[AnyContent] = Action {
    Redirect(oauthService.getAuthorizationUrl(appId), 302)
  }

  def testCallback(appId: String, accessTokenValue: String): Action[AnyContent] = Action.async { implicit request =>
    socialServiceConnectors.provideByAppId(toServiceType(appId)) match {
      case Some(socialServiceConnector) =>
        val accessToken = OAuth2Service.AccessToken(accessTokenValue)

        (for {
          person <- socialServiceConnector.requestUserProfile(accessToken)
          userId <- dataAccessManager.addUser(User(ZonedDateTime.now), person)
          sessionId <- dataAccessManager.createSession(accessToken, userId)
        } yield {
          Redirect(routes.SocialRelationsController.home()).withSession(
            UserSessionId -> sessionId.value
          )
        }).recover {
          case e => Unauthorized(e.getMessage)
        }
      case None => Future(NotImplemented(s"Platform '$appId' is not supported"))
    }
  }

  def callback(appId: String, code: String): Action[AnyContent] = Action.async { implicit request =>
    socialServiceConnectors.provideByAppId(toServiceType(appId)) match {
      case Some(socialServiceConnector) =>
        (for {
          accessToken <- oauthService.getToken(appId, code)
          person <- socialServiceConnector.requestUserProfile(accessToken)
          userId <- dataAccessManager.addUser(User(ZonedDateTime.now), person)
          sessionId <- dataAccessManager.createSession(accessToken, userId)
        } yield {
          Redirect(routes.SocialRelationsController.home()).withSession(
            UserSessionId -> sessionId.value
          )
        }).recover {
          case e => Unauthorized(e.getMessage)
        }
      case None => Future(NotImplemented(s"Platform '$appId' is not supported"))
    }
  }

  private def toServiceType(name: String): ServiceType = {
    name match {
      case "facebook" => ServiceType.Facebook
      case "linkedin" => ServiceType.Linkedin
      case "instagram" => ServiceType.Instagram
      case _ => throw new IllegalArgumentException("unknown platform ID")
    }
  }
}
