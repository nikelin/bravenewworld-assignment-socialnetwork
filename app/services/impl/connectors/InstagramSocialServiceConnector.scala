package services.impl.connectors

import javax.inject.Inject

import com.typesafe.config.Config
import models.PersonAttributeValue.{Interest, WorkExperience}
import models._
import play.api.libs.json.JsArray
import play.api.libs.ws.WSClient
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector

import scala.concurrent.{ExecutionContext, Future}

class InstagramSocialServiceConnector @Inject() (wsClient: WSClient, config: Config) extends SocialServiceConnector {
  import SocialServiceConnector._

  override val serviceType = ServiceType.Instagram

  override def requestInterestsList(accessToken: Option[AccessToken], userId: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = {
    throw new IllegalStateException()
  }

  override def requestWorkExperience(accessToken: Option[AccessToken], userId: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = {
    throw new IllegalStateException()
  }

  override def requestFriendsList(accessTokenOpt: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    accessTokenOpt.map(accessToken =>
      wsClient.url(endpointUrl(config) + "/v1/users/self/followed-by")
        .withQueryString("access_token" -> accessToken.value)
        .get map { response =>
        response.json.as[JsArray].value map { result =>
          val name = (result \ "full_name").as[String]
          val id = (result \ "id").as[String]
          val photo = (result \ "profile_picture").asOpt[String]

          PersonWithAttributes(Person(UserAccountId.InstagramId(id), isIdentity = false),
            Seq(
              PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString, name))
            ) ++ photo.map( p =>
              Seq(PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(p)))
            ).getOrElse(Seq.empty)
          )
        }
      }
    ).getOrElse(Future.successful(Seq.empty[PersonWithAttributes]))
  }

  override def requestUserProfile(accessToken: AccessToken)(implicit ec: ExecutionContext): Future[PersonWithAttributes] = {
    wsClient.url(endpointUrl(config) + "/v1/users/self")
      .withQueryString("access_token" -> accessToken.value)
      .get map { response =>
      val name = (response.json \ "data" \ "full_name").as[String]
      val id = (response.json \ "data" \ "id").as[String]
      val photo = (response.json \ "data" \ "profile_picture").asOpt[String]
      val userName = (response.json \ "data" \ "username").as[String]
      val follows = (response.json \ "data" \ "counts" \ "follows").as[Int]
      val followedBy = (response.json \ "data" \ "counts" \ "followed_by").as[Int]
      val media = (response.json \ "data" \ "counts" \ "media").as[Int]

      val socialAttributes = Seq(
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.FollowedByCount.asString, followedBy.toString)),
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.FollowsCount.asString, follows.toString)),
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.ContentCreatedCount.asString, media.toString)),
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.UserName.asString, userName))
      )

      val photoAttribute = photo.map( p =>
        Seq(PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(p)))
      ).getOrElse(Seq.empty)

      val basicAttributes = Seq(
        PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString, if(name.isEmpty) userName else name))
      )

      PersonWithAttributes(Person(UserAccountId.InstagramId(id), isIdentity = true), socialAttributes ++ photoAttribute ++ basicAttributes)
    }
  }
}
