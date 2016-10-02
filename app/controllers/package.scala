import dal.DataAccessManager
import models.{Id, MaterializedEntity, User, UserSession}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

package object controllers {
  final val UserSessionId: String = "authenticated.sessionId"

  def IsAuthenticated[T](dataAccessManager: DataAccessManager)
                        (block: (Request[AnyContent], MaterializedEntity[User]) => Future[Result])
                        (implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      request.session.get(UserSessionId) match {
        case Some(userSessionId) =>
          dataAccessManager.findUserBySessionId(Id[UserSession](userSessionId)) flatMap {
            case Some(user) => block(request, user)
            case None => Future(Results.Redirect(routes.AppController.index(), 302))
          }
        case None => Future(Results.Redirect(routes.AppController.index(), 302))
      }
    }


}
