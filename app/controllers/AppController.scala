package controllers

import play.api.mvc.{Action, AnyContent, Controller}

class AppController extends Controller {
  def logout(): Action[AnyContent] = Action {
    Redirect(routes.AppController.index())
      .withNewSession
  }

  def index(): Action[AnyContent] = Action {
    Ok(views.html.index.main())
  }
}
