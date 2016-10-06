package services.selenium

import java.io.File
import javax.inject.{Inject, Singleton}

import com.machinepublishers.jbrowserdriver._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class SeleniumDriversFactory @Inject() (settings: Config) extends BasePooledObjectFactory[WebDriver] with LazyLogging {
  import utils._

  override def wrap(obj: WebDriver): PooledObject[WebDriver] = new DefaultPooledObject[WebDriver](obj)

  override def create(): WebDriver = {
    new JBrowserDriver(
      Settings.builder()
        .cache(true)
        .userDataDirectory(new File("C:/SubWork/bnw/userData"))
        .cacheDir(new File("C:/SubWork/bnw/cache"))
        .maxRouteConnections(100)
        .saveMedia(true)
        .requestHeaders(RequestHeaders.CHROME)
        .userAgent(UserAgent.CHROME)
        .headless(true)
        .logJavascript(true)
        .cache(true)
        .maxConnections(100)
        .quickRender(true)
        .hostnameVerification(false)
        .processes(10)
        .timezone(Timezone.AMERICA_NEWYORK)
        .build()
    )
  }

  override def activateObject(p: PooledObject[WebDriver]): Unit = {
    "clear cookies" timing { p.getObject.manage().deleteAllCookies() }

    "initial" timing { p.getObject.get("https://m.facebook.com") }

    logger.info(s"Initial page loaded: ${p.getObject.getCurrentUrl}")

    val email =
      "email" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='m_login_email']"))) }

    val pass =
      "password" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='password-plain-text-toggle-input']"))) }

    val loginForm = p.getObject.findElement(By.cssSelector("form[data-sigil='m_login_form']"))

    "email:sendKeys" timing { email.sendKeys("testatestc@nikelin.ru") }
    "email:password" timing { pass.sendKeys("testatestc") }

    "form:submit" timing { loginForm.submit() }
  }

  override def passivateObject(p: PooledObject[WebDriver]): Unit = {
    "clear cookies" timing { p.getObject.manage().deleteAllCookies() }
  }
}