package services.impl.connectors

import java.time.Instant
import javax.inject.Named

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dal.DataAccessManager
import io.github.andrebeat.pool.Pool
import models.PersonAttributeValue.{Interest, WorkExperience}
import models._
import org.apache.commons.pool2.ObjectPool
import org.openqa.selenium.{By, JavascriptExecutor, WebDriver}
import play.api.libs.ws.WSClient
import services.oauth.OAuth2Service.AccessToken

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

class SeleniumFacebookSocialServiceConnector(seleniumDriversPool: Pool[WebDriver],
                                             dataAccessManager: DataAccessManager,
                                             config: Config,
                                             wsClient: WSClient)(implicit system: ActorSystem)
  extends FacebookSocialServiceConnector(config, wsClient)
    with LazyLogging {

  import services.oauth.SocialServiceConnector._
  import utils._

  override def requestInterestsList(accessToken: Option[AccessToken], person: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = ???

  override def requestWorkExperience(accessToken: Option[AccessToken], person: Id[Person])(implicit ec: ExecutionContext): Future[Iterable[PersonAttribute]] = ???

  override def requestFriendsList(accessToken: Option[AccessToken], userId: UserAccountId)(implicit ec: ExecutionContext): Future[Iterable[PersonWithAttributes]] = {
    userId match {
      case userId: UserAccountId.FacebookId =>
        val personAttributes = mutable.ListBuffer[PersonAttribute]()

        logger.info(s"Drivers pool state ${seleniumDriversPool.size()} available " +
          s"/ ${seleniumDriversPool.capacity} capacity")

        logger.info(s"Drivers pool state ${seleniumDriversPool.live()} live " +
          s"/ ${seleniumDriversPool.leased()} leased")

        seleniumDriversPool.tryAcquire(5.seconds) match {
          case Some(driverLease) ⇒
            val future = Future(driverLease) flatMap { driverLease ⇒
              driverLease use { driver ⇒
                val friendsPage = userId.tpe match {
                  case UserAccountId.FacebookId.FacebookIdType.AppScopedId =>
                    val scopedPageUrl = s"https://m.facebook.com/app_scoped_user_id/${userId.value}"
                    Await.ready(Future(driver.get(scopedPageUrl)), 20.seconds)

                    val idToken = "id="
                    val idTokenIdx = driver.getCurrentUrl.indexOf(idToken)

                    if (idTokenIdx != -1) {
                      val andIdx = driver.getCurrentUrl.indexOf("&", idTokenIdx)
                      val idTokenEndIdx = if (andIdx != -1) andIdx else driver.getCurrentUrl.length

                      val url = driver.getCurrentUrl + "&v=friends"
                      val id = driver.getCurrentUrl.substring(idTokenIdx + idToken.length, idTokenEndIdx)
                      personAttributes += PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(
                        PersonProfileField.GlobalScopedId.asString, id))
                      url
                    } else {
                      val userName = driver.getCurrentUrl.substring(driver.getCurrentUrl.lastIndexOf("/") + 1)
                      personAttributes += PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(
                        PersonProfileField.UserName.asString, userName))
                      driver.getCurrentUrl.substring(0, driver.getCurrentUrl.indexOf("?")) + "/friends"
                    }
                  case UserAccountId.FacebookId.FacebookIdType.GlobalScopedId =>
                    "https://m.facebook.com/profile.php?id=" + userId.value + "&v=friends"
                  case UserAccountId.FacebookId.FacebookIdType.Username =>
                    "https://m.facebook.com/" + userId.value + "/friends"
                }

                "friends page" timing {
                  logger.info(s"Fetching page $friendsPage")
                  Await.ready(Future(driver.get(friendsPage)), 20.seconds)
                }

                "scrolling" timing {
                  val jsExecutor = driver.asInstanceOf[JavascriptExecutor]
                  var prevOffset = -1L
                  var newOffset = jsExecutor.executeScript("return window.scrollY").asInstanceOf[Long]
                  while (prevOffset != newOffset && jsExecutor.executeScript("return window.scrollY < document.body.offsetHeight - 200").asInstanceOf[Boolean]) {
                    jsExecutor.executeScript("window.scrollTo(0, document.body.offsetHeight)")
                    prevOffset = newOffset
                    newOffset = jsExecutor.executeScript("return window.scrollY").asInstanceOf[Long]
                    Thread.sleep(2.seconds.toMillis)
                  }
                }

                val friendsList = "parsing" timing {
                  val friendLinks = driver.findElements(By.cssSelector("a[data-store*=hf]"))

                  val personsData = friendLinks.toList map { link =>
                    val profileNode = link.findElement(By.xpath("../../../../../div"))
                    val friendPictureNode = profileNode.findElement(By.xpath("a/i"))

                    val friendPictureNodeStyle = friendPictureNode.getCssValue("background")

                    val friendPictureUrlStartToken = "url("
                    val friendPictureUrlStartIndex = friendPictureNodeStyle.indexOf(friendPictureUrlStartToken)

                    val friendPictureUrl = friendPictureNodeStyle.substring(friendPictureUrlStartIndex + friendPictureUrlStartToken.length,
                      friendPictureNodeStyle.indexOf(")", friendPictureUrlStartIndex))
                    val friendNameValue = friendPictureNode.getAttribute("aria-label")

                    val dataStore = link.getAttribute("data-store")
                    val startToken = "\"id\":"
                    val tokenStart = dataStore.indexOf(startToken)
                    val tokenEnd = dataStore.indexOf(",", tokenStart)

                    val id = dataStore.substring(tokenStart + startToken.length, tokenEnd)
                    (id, friendPictureUrl, friendNameValue)
                  }

                  personsData map { case (id, pictureUrl, name) =>
                    val person = Person(UserAccountId.FacebookId(id, UserAccountId.FacebookId.FacebookIdType.GlobalScopedId), isIdentity = false)
                    val personAttributes = Seq(
                      PersonAttribute(PersonAttributeType.Text)(PersonAttributeValue.Text(PersonProfileField.Name.asString, name)),
                      PersonAttribute(PersonAttributeType.Photo)(PersonAttributeValue.Photo(pictureUrl))
                    )

                    dataAccessManager.updateOrCreatePerson(person) flatMap { record ⇒
                      dataAccessManager.updatePersonAttributes(record, personAttributes) map { _ ⇒
                        PersonWithAttributes(person, personAttributes)
                      }
                    }
                  }
                }

                logger.info(s"${friendsList.size} relations fetched")

                Future.sequence(friendsList)
              }
            }

            future onComplete { _ ⇒
              driverLease.release()

              logger.info(s"[RELEASE] Drivers pool state ${seleniumDriversPool.size()} available " +
                s"/ ${seleniumDriversPool.capacity} capacity")

              logger.info(s"[RELEASE] Drivers pool state ${seleniumDriversPool.live()} live " +
                s"/ ${seleniumDriversPool.leased()} leased")
            }

            future
          case None ⇒
            Future.failed(new IllegalStateException("failed to aquire driver instance"))
        }
      case _ =>
        throw new IllegalArgumentException("unsupported ID type")
    }
  }
}
