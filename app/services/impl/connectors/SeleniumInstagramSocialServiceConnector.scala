package services.impl.connectors
import com.typesafe.config.Config
import dal.DataAccessManager
import models.UserAccountId
import org.apache.commons.pool2.ObjectPool
import org.openqa.selenium.WebDriver
import play.api.libs.ws.WSClient
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector.PersonWithAttributes

import scala.concurrent.{ExecutionContext, Future}

class SeleniumInstagramSocialServiceConnector(config: Config,
                                              dataAccessManager: DataAccessManager,
                                              webDriversPool: ObjectPool[WebDriver],
                                              wsClient: WSClient)
  extends InstagramSocialServiceConnector(wsClient, config) {
  override def requestFriendsList(accessTokenOpt: Option[AccessToken],
                                  userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    throw new IllegalStateException()
  }
}
