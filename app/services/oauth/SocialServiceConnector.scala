package services.oauth

import com.typesafe.config.Config
import models._

import scala.concurrent.{ExecutionContext, Future}

object SocialServiceConnector {
  case class PersonWithAttributes(person: Person, attribute: Seq[PersonAttribute])
}

trait SocialServiceConnector {
  import SocialServiceConnector._

  val serviceType: ServiceType

  def requestInterestsList(accessToken: Option[OAuth2Service.AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonAttributeValue.Interest]]

  def requestWorkExperience(accessToken: Option[OAuth2Service.AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonAttributeValue.WorkExperience]]

  def requestFriendsList(accessToken: Option[OAuth2Service.AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]]

  def requestUserProfile(accessToken: OAuth2Service.AccessToken)(implicit ec: ExecutionContext): Future[PersonWithAttributes]

  protected def endpointUrl(config: Config): String = config.getString(s"oauth.${serviceType.asString}.endpointUrl")

  protected def oauthClientId(config: Config): String = config.getString(s"oauth.${serviceType.asString}.appId")

}
