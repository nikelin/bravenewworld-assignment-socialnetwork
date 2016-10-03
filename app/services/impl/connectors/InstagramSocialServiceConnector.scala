package services.impl.connectors

import javax.inject.Inject

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models.{Person, ServiceType, UserAccountId, UserProfile}
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient
import services.OAuth2Service.AccessToken
import services.SocialServiceConnector

import scala.concurrent.{ExecutionContext, Future}

class InstagramSocialServiceConnector @Inject() (wsClient: WSClient, config: Config) extends SocialServiceConnector {

  override val serviceType = ServiceType.Instagram

  override def requestInterestsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Interest]] = {
    throw new IllegalStateException()
  }

  override def requestWorkExperience(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[WorkExperience]] = {
    throw new IllegalStateException()
  }

  override def requestFriendsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Person]] = {
    wsClient.url(endpointUrl(config) + "/v1/users/self/followed-by")
      .withQueryString("access_token" -> accessToken.value)
      .get map { response =>
      response.json.as[JsArray].value map { result =>
        val name = (result \ "full_name").as[String]
        val id = (result \ "id").as[String]
        val photo = (result \ "profile_picture").as[String]

        Person(UserAccountId.InstagramId(id), UserProfile(name, Some(photo)))
      }
    }
  }

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[Person] = {
    wsClient.url(endpointUrl(config) + "/v1/users/self")
      .withQueryString("access_token" -> accessToken.value)
      .get map { response =>
      val name = (response.json \ "data" \ "full_name").as[String]
      val id = (response.json \ "data" \ "id").as[String]
      val photo = (response.json \ "data" \ "profile_picture").as[String]

      Person(UserAccountId.InstagramId(id), UserProfile(name, Some(photo)))
    }
  }
}
