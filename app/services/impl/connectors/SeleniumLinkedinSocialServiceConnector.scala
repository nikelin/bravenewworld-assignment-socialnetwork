package services.impl.connectors

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import models._
import org.apache.commons.pool2.ObjectPool
import org.openqa.selenium.WebDriver
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.WSClient
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector.PersonWithAttributes

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class SeleniumLinkedinSocialServiceConnector(config: Config, wsClient: WSClient,
                                             dataAccessManager: DataAccessManager,
                                             webDriversPool: ObjectPool[WebDriver])
  extends LinkedinSocialServiceConnector(config, wsClient) with LazyLogging {
  override def requestFriendsList(accessToken: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    userId match {
      case id: UserAccountId.LinkedinId =>
        val driver = webDriversPool.borrowObject()

        def runPaged(offset: Int, step: Int, total: Int, memberId: String, results: Seq[PersonWithAttributes]): Seq[PersonWithAttributes] = {
          driver.get(s"https://www.linkedin.com/profile/profile-v2-connections?id=$memberId&offset=$offset&count=10&distance=1")

          val htmlData = driver.getPageSource

          val jsonDataStartToken = "\">"
          val jsonDataEndToken = "</pre>"
          val jsonDataStart = htmlData.indexOf(jsonDataStartToken)
          val jsonDataEnd = htmlData.lastIndexOf(jsonDataEndToken)
          val jsonData = htmlData.substring(jsonDataStart + jsonDataStartToken.length, jsonDataEnd)

          val parsedJson = Json.parse(jsonData)
          val connections = (parsedJson \ "content" \ "connections" \ "connections").as[JsArray]
          val totalConnections = (parsedJson \ "content" \ "connections" \ "numAll").as[Int]

          val currentConnections = connections.value map { connection =>
            PersonWithAttributes(
              Person(UserAccountId.LinkedinId((connection \ "memberID").as[Int].toString)),
              Seq(
                PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString,
                  (connection \ "fmt__full_name").as[String])),
                PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.GlobalScopedId.asString,
                  (connection \ "memberID").as[Int].toString))
              ) ++ (connection \ "mem_pic").asOpt[String].map( pic =>
                Seq(PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(pic)))
              ).getOrElse(Seq.empty)
            )
          }

          val subResult = results ++ currentConnections

          val totalConnectionsUpdated = if(total == -1) totalConnections else total
          if ( total == -1 || totalConnectionsUpdated - offset > step ) {
            Thread.sleep(1.second.toMillis)
            runPaged(offset + step, step, totalConnectionsUpdated, memberId, subResult)
          } else {
            subResult
          }
        }

        val personAttributes = mutable.ListBuffer[PersonAttribute]()

        val future = for {
          personOpt <- dataAccessManager.findPersonByInternalId(id)
          if personOpt.nonEmpty
          person = personOpt.get
          attributes <- dataAccessManager.findPersonAttributesByPersonId(person.id)

          globalScopedIdOpt = attributes.find(a =>
            a.tpe == PersonAttributeType.Text &&
              a.value.asInstanceOf[PersonAttributeValue.Text].name == PersonProfileField.GlobalScopedId.asString
          )

          userNameAttributeOpt = attributes.find(a =>
            a.tpe == PersonAttributeType.Text &&
              a.value.asInstanceOf[PersonAttributeValue.Text].name == PersonProfileField.UserName.asString
          )

          personOpt <- dataAccessManager.findPersonByInternalId(userId)
          if personOpt.isDefined
          person = personOpt.get

          result <- Future {
            val memberIdValue =
              (globalScopedIdOpt, userNameAttributeOpt) match {
                case (_, Some(userNameAttribute)) =>
                  driver.get(s"https://linkedin.com/in/${userNameAttribute.value.asInstanceOf[PersonAttributeValue.Text].value}")

                  val memberIdStartToken = "memberId    : \""
                  val memberIdStartIdx = driver.getPageSource.indexOf(memberIdStartToken)
                  val memberIdEndIdx = driver.getPageSource.indexOf("\"", memberIdStartIdx + memberIdStartToken.length)

                  val memberId = driver.getPageSource.substring(memberIdStartIdx + memberIdStartToken.length, memberIdEndIdx).replaceAll("\"", "")

                  personAttributes += PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.GlobalScopedId.asString, memberId))

                  memberId
                case (Some(memberId), _) => memberId.value.asInstanceOf[PersonAttributeValue.Text].value
                case _ => throw new IllegalArgumentException(s"corrupted person record #${person.id.value}")
              }

            runPaged(0, 10, -1, memberIdValue, Seq.empty[PersonWithAttributes]) map { person =>
              logger.info(s"Person #${person.person.internalId} fetched")
              person
            }
          }

          _ <- dataAccessManager.updatePersonAttributes(person.id, personAttributes)
        } yield  result

        future onComplete { _ =>
          webDriversPool.returnObject(driver)
        }

        future
      case _ => Future.failed(new IllegalArgumentException("unsupported account ID"))
    }
  }
}
