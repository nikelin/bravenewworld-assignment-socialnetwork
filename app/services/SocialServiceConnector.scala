package services

import com.typesafe.config.Config
import models._

import scala.concurrent.{ExecutionContext, Future}

trait SocialServiceConnector {
  val serviceType: ServiceType

  def requestInterestsList(accessToken: OAuth2Service.AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[PersonAttributeValue.Interest]]

  def requestWorkExperience(accessToken: OAuth2Service.AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[PersonAttributeValue.WorkExperience]]

  def requestFriendsList(accessToken: OAuth2Service.AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Person]]

  def requestUserProfile(accessToken: OAuth2Service.AccessToken)(implicit ec: ExecutionContext): Future[Person]

  protected def endpointUrl(config: Config): String = config.getString(s"oauth.${serviceType.asString}.endpointUrl")

  protected def oauthClientId(config: Config): String = config.getString(s"oauth.${serviceType.asString}.appId")

}
