package services.impl.connectors

import javax.inject.Inject

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models.UserAccountId.FacebookId
import models.{Person, ServiceType, UserAccountId, UserProfile}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsArray, JsReadable, JsValue}
import play.api.libs.ws.{WSClient, WSRequest}
import services.OAuth2Service.AccessToken
import services.{OAuth2Service, SocialServiceConnector}

import scala.concurrent.{ExecutionContext, Future}

class FacebookSocialServiceConnector @Inject() (config: Config, wsClient: WSClient) extends SocialServiceConnector {
  override val serviceType = ServiceType.Facebook

  override def requestInterestsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Interest]] = ???

  override def requestWorkExperience(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[WorkExperience]] = ???

  override def requestFriendsList(accessToken: AccessToken, userId: UserAccountId)(implicit ec: ExecutionContext): Future[Seq[Person]] = {
    userId match {
      case FacebookId(accountId) =>
        fetchPagedResult(
          JsArray(),
          wsClient.url(s"${endpointUrl(config)}/v2.7/$accountId/friends")
            .withQueryString(
              "fields" -> "name,id,photo",
              "client_id" -> oauthClientId(config),
              "access_token" -> accessToken.value
            )
            .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON),
          accessToken
        ) map { results =>
          results.value map { result =>
            Person(
              UserAccountId.FacebookId((result \ "id").as[String]),
              UserProfile(
                (result \ "name").as[String],
                (result \ "photo").asOpt[String]
              )
            )
          }
        }
      case e: Any => Future.failed(new IllegalArgumentException(s"incorrect ID type: $e"))
    }
  }

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[Person] = {
    endpointRequest("/v2.5/me", accessToken, ("fields", "name,id,picture"))
      .get
      .map { response =>
        val name = (response.json \ "name").as[String]
        val id = (response.json \ "id").as[String]
        val picture = (response.json \ "picture" \ "data" \ "url").asOpt[String]

        Person(UserAccountId.FacebookId(id), UserProfile(name, picture))
      }
  }

  private def fetchPagedResult(buffer: JsArray,
                               request: WSRequest,
                               accessToken: AccessToken)(implicit ec: ExecutionContext): Future[JsArray] = {
    request.get flatMap { response =>
      val nextChunkLinkOpt = (response.json \ "paging" \ "next").asOpt[String]
      val currentChunkData = (response.json \ "data").as[JsArray]

      nextChunkLinkOpt match {
        case Some(nextChunkLink) =>
          fetchPagedResult(buffer :+ currentChunkData, endpointRequest(nextChunkLink, accessToken), accessToken)
        case None =>
          Future(buffer ++ currentChunkData)
      }
    }
  }

  private def endpointRequest(apiMethod: String,
                              accessToken: OAuth2Service.AccessToken,
                              additionalParams: (String, String)*)(implicit ec: ExecutionContext): WSRequest = {
    wsClient.url(s"${endpointUrl(config)}$apiMethod")
      .withQueryString(
        "client_id" -> oauthClientId(config),
        "access_token" -> accessToken.value,
        "fields" -> "id, name, picture"
      )
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
  }

}
