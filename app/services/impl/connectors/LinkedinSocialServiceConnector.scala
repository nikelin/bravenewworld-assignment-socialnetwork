package services.impl.connectors

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.WSClient
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector

import scala.concurrent.{ExecutionContext, Future}

class LinkedinSocialServiceConnector(config: Config, wsClient: WSClient) extends SocialServiceConnector {
  import SocialServiceConnector._

  override val serviceType: ServiceType = ServiceType.Linkedin

  override def requestInterestsList(accessToken: Option[AccessToken], person: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = {
    throw new IllegalStateException()
  }

  override def requestWorkExperience(accessToken: Option[AccessToken], person: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = {
    throw new IllegalStateException()
  }

  override def requestFriendsList(accessToken: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    throw new IllegalStateException()
  }

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[PersonWithAttributes] = {
    wsClient.url(endpointUrl(config) + "/v1/people/~:(id,public-profile-url,firstName,lastName,picture-url)")
      .withQueryString(
        "oauth2_access_token" -> accessToken.value,
        "format" -> "json"
      )
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .get map { response =>
      val id = (response.json \ "id").as[String]
      val firstName = (response.json \ "firstName").as[String]
      val lastName = (response.json \ "lastName").as[String]
      val pictureUrl = (response.json \ "pictureUrl").asOpt[String]
      val publicProfileUrl = (response.json \ "publicProfileUrl").as[String]

      PersonWithAttributes(Person(UserAccountId.LinkedinId(id)), Seq(
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString, firstName + " " + lastName)),
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.UserName.asString,
          publicProfileUrl.substring(publicProfileUrl.indexOf("/in/") + 4))))
        ++ pictureUrl.map(picture => Seq(PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(picture)))).getOrElse(Seq.empty)
      )
    }
  }
}
