package services.impl.connectors

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models.{Person, ServiceType, UserAccountId, UserProfile}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.WSClient
import services.OAuth2Service.AccessToken
import services.{OAuth2Service, SocialServiceConnector}

import scala.concurrent.{ExecutionContext, Future}

class LinkedinSocialServiceConnector(config: Config, wsClient: WSClient) extends SocialServiceConnector {
  override val serviceType: ServiceType = ServiceType.Linkedin

  override def requestInterestsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Interest]] = {
    throw new IllegalStateException()
  }

  override def requestWorkExperience(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[WorkExperience]] = {
    throw new IllegalStateException()
  }

  override def requestFriendsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Person]] = {
    throw new IllegalStateException()
  }

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[Person] = {
    wsClient.url(endpointUrl(config) + "/v1/people/~:(id,firstName,lastName,picture-url)")
      .withQueryString(
        "oauth2_access_token" -> accessToken.value,
        "format" -> "json"
      )
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .get map { response =>
      val id = (response.json \ "id").as[String]
      val firstName = (response.json \ "firstName").as[String]
      val lastName = (response.json \ "lastName").as[String]
      val pictureUrl = (response.json \ "pictureUrl").as[String]
      Person(UserAccountId.LinkedinId(id), UserProfile(firstName + " " + lastName, Some(pictureUrl)))
    }
  }
}
