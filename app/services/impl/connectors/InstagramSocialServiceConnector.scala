package services.impl.connectors

import javax.inject.Inject

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models.{Person, ServiceType, UserAccountId}
import play.api.libs.ws.WSClient
import services.OAuth2Service.AccessToken
import services.{SocialServiceConnector}

import scala.concurrent.{ExecutionContext, Future}

class InstagramSocialServiceConnector @Inject() (wsClient: WSClient, config: Config) extends SocialServiceConnector {

  override val serviceType = ServiceType.Instagram

  override def requestInterestsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Interest]] = ???

  override def requestWorkExperience(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[WorkExperience]] = ???

  override def requestFriendsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Person]] = ???

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[Person] = ???
}
