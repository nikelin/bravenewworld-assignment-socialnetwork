package services

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import models.{ServiceType, UserAccountId}
import play.api.libs.ws.WSClient
import services.impl.connectors.{FacebookSocialServiceConnector, InstagramSocialServiceConnector, LinkedinSocialServiceConnector}

@Singleton
class SocialServiceConnectors @Inject() (wsClient: WSClient, config: Config) {

  private final val connectors = Seq[SocialServiceConnector](
    new FacebookSocialServiceConnector(config, wsClient),
    new InstagramSocialServiceConnector(wsClient, config),
    new LinkedinSocialServiceConnector(config, wsClient)
  )

  def provideByAppId(serviceType: ServiceType): Option[SocialServiceConnector] = {
    connectors.find(_.serviceType == serviceType)
  }

}
