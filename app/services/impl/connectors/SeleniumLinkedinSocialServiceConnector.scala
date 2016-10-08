package services.impl.connectors

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import models._
import org.apache.commons.pool2.ObjectPool
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, WebDriver}
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.libs.ws.WSClient
import services.oauth.OAuth2Service.AccessToken
import services.oauth.SocialServiceConnector.PersonWithAttributes

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.util.control.NonFatal

class SeleniumLinkedinSocialServiceConnector(config: Config, wsClient: WSClient,
                                             dataAccessManager: DataAccessManager,
                                             webDriversPool: ObjectPool[WebDriver])
  extends LinkedinSocialServiceConnector(config, wsClient) with LazyLogging {


  override def requestInterestsList(accessToken: Option[AccessToken], personId: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = {
    logger.info("Borrowing web driver object")
    val driver = webDriversPool.borrowObject()
    logger.info("WebDriver instance obtained")

    val future = dataAccessManager.findPersonAttributesByPersonId(personId) map { attributes =>
      val userNameAttribute = attributes.find(a =>
        a.tpe == PersonAttributeType.Text
          && a.value.asInstanceOf[PersonAttributeValue.Text].name == PersonProfileField.UserName.asString
      ).map(_.value.asInstanceOf[PersonAttributeValue.Text])

      userNameAttribute match {
        case Some(userName) =>
          val userPageUrl = s"https://linkedin.com/in/${userName.value}"
          logger.info(s"Requesting user page to fetch updated data: $userPageUrl")
          driver.get(userPageUrl)
          Thread.sleep(5.seconds.toMillis)

          val endorsements = driver.findElements(By.cssSelector("li[data-endorsed-item-name]"))

          endorsements.toList map { endorsement =>
            PersonAttribute(PersonAttributeType.Interest)(PersonAttributeValue.Interest(endorsement.getAttribute("data-endorsed-item-name")))
          }
        case None =>
          logger.info("Username is not available")
          throw new IllegalStateException("user name is not available")
      }
    }

    future onComplete (_ =>
      webDriversPool.returnObject(driver)
    )

    future recover {
      case e if NonFatal(e) =>
        logger.error(e.getMessage, e)
        throw e
    }
  }

  override def requestFriendsList(accessToken: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    logger.info(s"Update friends list for $userId")

    userId match {
      case id: UserAccountId.LinkedinId =>
        def runPaged(driver: WebDriver, offset: Int, step: Int, total: Int, memberId: String, results: Seq[PersonWithAttributes]): Seq[PersonWithAttributes] = {

          logger.info("Fetching connections from " + s"https://www.linkedin.com/profile/profile-v2-connections?id=$memberId&offset=$offset&count=10&distance=1")

          driver.get(s"https://www.linkedin.com/profile/profile-v2-connections?id=$memberId&offset=$offset&count=10&distance=1")

          val htmlData = driver.getPageSource

          val jsonDataStartToken = "\">"
          val jsonDataEndToken = "</pre>"
          val jsonDataStart = htmlData.indexOf(jsonDataStartToken)
          val jsonDataEnd = htmlData.lastIndexOf(jsonDataEndToken)
          val jsonData = htmlData.substring(jsonDataStart + jsonDataStartToken.length, jsonDataEnd)

          val parsedJson = Json.parse(jsonData)
          val connectionsNode = (parsedJson \ "content" \ "connections").as[JsObject]
          val connections =
            if (connectionsNode.fields.nonEmpty ) (connectionsNode \ "connections").as[JsArray].value
            else Seq.empty

          connections match {
            case Nil => Seq.empty
            case _ =>
              val totalConnections = (connectionsNode \ "numAll").as[Int]

              val currentConnections = connections map { connection =>
                val profileViewUrl = (connection \ "pview").as[String]
                logger.info(s"Requesting connection profile URL $profileViewUrl", profileViewUrl)
                driver.get(profileViewUrl)

                val endorsements = new WebDriverWait(driver, 5.seconds.toMillis).until(
                  ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("li[data-endorsed-item-name]")))
                val interests = endorsements.toList map { endorsement =>
                  PersonAttribute(PersonAttributeType.Interest)(PersonAttributeValue.Interest(endorsement.getAttribute("data-endorsed-item-name")))
                }

                PersonWithAttributes(
                  Person(UserAccountId.LinkedinId((connection \ "memberID").as[Int].toString)),
                  Seq(
                    PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString,
                      (connection \ "fmt__full_name").as[String])),
                    PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.GlobalScopedId.asString,
                      (connection \ "memberID").as[Int].toString))
                  ) ++ (connection \ "mem_pic").asOpt[String].map(pic =>
                    Seq(PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(pic)))
                  ).getOrElse(Seq.empty) ++ interests
                )
              }

              val subResult = results ++ currentConnections

              val totalConnectionsUpdated = if (total == -1) totalConnections else total
              if (totalConnectionsUpdated - offset > step && step < totalConnectionsUpdated) {
                Thread.sleep(1.second.toMillis)
                runPaged(driver, offset + step, step, totalConnectionsUpdated, memberId, subResult)
              } else {
                subResult
              }
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

          driver <- Future { webDriversPool.borrowObject() }

          result <- {
            val future = Future {
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
                  case _ =>
                    logger.error("Corruped person record")
                    throw new IllegalArgumentException(s"corrupted person record #${person.id.value}")
                }

              runPaged(driver, 0, 10, -1, memberIdValue, Seq.empty[PersonWithAttributes]) map { person =>
                logger.info(s"Person #${person.person.internalId} fetched")
                person
              }
            }

            future onComplete { _ =>
              logger.info("Future has been completed")
              webDriversPool.returnObject(driver)
            }

            future
          }
        } yield  result

        future recover {  case e if NonFatal(e) => logger.error(e.getMessage, e); throw e }
      case _ => Future.failed(new IllegalArgumentException("unsupported account ID"))
    }
  }
}
