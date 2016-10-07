package services.oauth

import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorSystem
import com.typesafe.config.Config
import dal.DataAccessManager
import models.ServiceType
import modules.AppModule
import org.apache.commons.pool2.ObjectPool
import org.openqa.selenium.WebDriver
import play.api.libs.ws.WSClient
import services.impl.connectors.{InstagramSocialServiceConnector, LinkedinSocialServiceConnector, SeleniumFacebookSocialServiceConnector, SeleniumLinkedinSocialServiceConnector}

@Singleton
class SocialServiceConnectors @Inject() (wsClient: WSClient,
                                         @Named("facebook") facebookDriversPool: ObjectPool[WebDriver],
                                         @Named("linkedin") linkedinDriversPool: ObjectPool[WebDriver],
                                         @Named("instagram") instagramDriversPool: ObjectPool[WebDriver],
                                         system: ActorSystem,
                                         dataAccessManager: DataAccessManager,
                                         config: Config) {

  private final val connectors = Seq[SocialServiceConnector](
    new SeleniumFacebookSocialServiceConnector(facebookDriversPool, dataAccessManager, config, wsClient)(system),
    new InstagramSocialServiceConnector(wsClient, config),
    new SeleniumLinkedinSocialServiceConnector(config, wsClient, dataAccessManager, linkedinDriversPool)
  )

  def provideByAppId(serviceType: ServiceType): Option[SocialServiceConnector] = {
    connectors.find(_.serviceType == serviceType)
  }

}
