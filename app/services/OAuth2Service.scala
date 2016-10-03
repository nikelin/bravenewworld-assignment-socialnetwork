package services

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import models.ServiceType
import play.api.Application
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.{WS, WSClient}
import play.api.mvc.Results

import scala.concurrent.{ExecutionContext, Future}

object OAuth2Service {

  case class AccessToken(value: String)

}

@Singleton
class OAuth2Service @Inject() (ws: WSClient, configuration: Config) extends LazyLogging {

  def oauthAuthId(appId: String): String = configuration.getString(s"oauth.$appId.appId")
  def oauthAuthSecret(appId: String): String = configuration.getString(s"oauth.$appId.secretKey")
  def oauthAccessTokenUrl(appId: String): String = configuration.getString(s"oauth.$appId.accessTokenUrl")
  def oauthRedirectUrl(appId: String): String = configuration.getString("serverAddress") + configuration.getString(s"oauth.$appId.redirectUrl")

  def getAuthorizationUrl(appId: String): String = {
    configuration.getString(s"oauth.$appId.authorizeUrl").format(oauthAuthId(appId), oauthRedirectUrl(appId))
  }

  def getAccessTokenUrl(appId: String, code: String): String = {
    oauthAccessTokenUrl(appId)
  }

  def getToken(appId: String, code: String)(implicit ec: ExecutionContext): Future[OAuth2Service.AccessToken] = {
    val tokenResponse = appId match {
      case ServiceType.Instagram.asString =>
        val queryString = s"code=$code&client_id=${oauthAuthId(appId)}&client_secret=${oauthAuthSecret(appId)}&redirect_uri=${oauthRedirectUrl(appId)}&grant_type=authorization_code"
        ws.url(oauthAccessTokenUrl(appId))
          .withHeaders(
            HeaderNames.CONTENT_TYPE -> MimeTypes.FORM
          )
          .post(queryString)
      case _ =>
        ws.url(oauthAccessTokenUrl(appId))
          .withQueryString(
            "code" -> code,
            "client_id" -> oauthAuthId(appId),
            "client_secret" -> oauthAuthSecret(appId),
            "redirect_uri" -> oauthRedirectUrl(appId),
            "grant_type" -> "authorization_code"
          )
          .withHeaders(
            HeaderNames.ACCEPT -> MimeTypes.JSON
          )
          .post(Results.EmptyContent())
    }

    tokenResponse.flatMap { response =>
      logger.info("Response", response.body)
      (response.json \ "access_token")
        .asOpt[String]
        .fold(Future.failed[OAuth2Service.AccessToken](new IllegalStateException("failed"))) { accessToken =>
        Future.successful(OAuth2Service.AccessToken(accessToken))
      }
    } recover {
      case e => logger.error("Access token request failed", e); throw e
    }
  }
}
