package controllers

import play.api.mvc.{Action, AnyContent, Controller}

class AppController extends Controller {
  def index(): Action[AnyContent] = Action {
    Ok(views.html.index.main())
  }
}
