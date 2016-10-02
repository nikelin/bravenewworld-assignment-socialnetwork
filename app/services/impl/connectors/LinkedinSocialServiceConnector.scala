package services.impl.connectors

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models.{Person, ServiceType, UserAccountId, UserProfile}
import play.api.libs.ws.WSClient
import services.OAuth2Service.AccessToken
import services.{OAuth2Service, SocialServiceConnector}

import scala.concurrent.{ExecutionContext, Future}

class LinkedinSocialServiceConnector(config: Config, wsClient: WSClient) extends SocialServiceConnector {
  override val serviceType: ServiceType = ServiceType.Linkedin

  override def requestInterestsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Interest]] = ???

  override def requestWorkExperience(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[WorkExperience]] = ???

  override def requestFriendsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Person]] = ???

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[Person] = ???
}
