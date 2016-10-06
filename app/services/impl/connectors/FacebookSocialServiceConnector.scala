package services.impl.connectors

import javax.inject.Inject

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models.UserAccountId.FacebookId
import models._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsArray, JsReadable, JsValue}
import play.api.libs.ws.{WSClient, WSRequest}
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector
import services.oauth.{OAuth2Service, SocialServiceConnector}

import scala.concurrent.{ExecutionContext, Future}

class FacebookSocialServiceConnector @Inject() (config: Config, wsClient: WSClient) extends SocialServiceConnector {
  import SocialServiceConnector._

  override val serviceType = ServiceType.Facebook

  override def requestInterestsList(accessToken: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[Interest]] = ???

  override def requestWorkExperience(accessToken: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[WorkExperience]] = ???

  override def requestFriendsList(accessTokenOpt: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    accessTokenOpt.map( accessToken =>
      userId match {
        case FacebookId(accountId, _) =>
          fetchPagedResult(
            JsArray(),
            wsClient.url(s"${endpointUrl(config)}/v2.7/me/friends")
              .withQueryString(
                "fields" -> "name,id,photo",
                "client_id" -> oauthClientId(config),
                "access_token" -> accessToken.value
              )
              .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON),
            accessToken
          ) map { results =>
            results.value map { result =>
              PersonWithAttributes(Person(UserAccountId.FacebookId((result \ "id").as[String], UserAccountId.FacebookId.FacebookIdType.AppScopedId)), Seq(
                PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString, (result \ "name").as[String]))
              ))
            }
          }
        case e: Any => Future.failed(new IllegalArgumentException(s"incorrect ID type: $e"))
      }
    ).getOrElse(Future.successful(Seq.empty[PersonWithAttributes]))
  }

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[PersonWithAttributes] = {
    endpointRequest("/v2.5/me", accessToken, ("fields", "name,username,id,picture"))
      .get
      .map { response =>
        val name = (response.json \ "name").as[String]
        val id = (response.json \ "id").as[String]
        val picture = (response.json \ "picture" \ "data" \ "url").asOpt[String]

        PersonWithAttributes(Person(UserAccountId.FacebookId(id, UserAccountId.FacebookId.FacebookIdType.AppScopedId)), Seq(
          PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString, name))
        ) ++ picture.map(p => Seq(PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(p)))).getOrElse(Seq.empty))
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
