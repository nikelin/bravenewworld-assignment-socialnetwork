package services.selenium

import javax.inject.Inject

import com.machinepublishers.jbrowserdriver.UserAgent
import com.typesafe.config.Config
import io.github.andrebeat.pool.{Pool, ReferenceType}
import org.apache.commons.pool2.PooledObject
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration._

import utils._

object LinkedinSeleniumDriversFactory {

  def activateObject(config: Config)(p: WebDriver): WebDriver = {
    "linkedin:clear cookies" timing { p.manage().deleteAllCookies() }

    "linkedin:initial" timing { p.get("https://www.linkedin.com/uas/login") }

    val email =
      "linkedin:email" timing { new WebDriverWait(p, 15).until(ExpectedConditions.presenceOfElementLocated(By.id("session_key-login"))) }

    val pass =
      "linkedin:password" timing { new WebDriverWait(p, 15).until(ExpectedConditions.presenceOfElementLocated(By.id("session_password-login"))) }

    val loginForm = p.findElement(By.id("login"))

    "linkedin:email:sendKeys" timing { email.sendKeys(config.getString("oauth.linkedin.botAccount.email")) }
    "linkedin:email:password" timing { pass.sendKeys(config.getString("oauth.linkedin.botAccount.password")) }

    "linkedin:form:submit" timing { loginForm.submit() }

    p
  }

  def create(config: Config): Pool[WebDriver] = {
    Pool(
      capacity = 1,
      factory = () ⇒ activateObject(config)(AbstractSeleniumDriversFactory.create(config)),
      referenceType = ReferenceType.Strong,
      maxIdleTime = 1.hour,
      reset = (p: WebDriver) ⇒ activateObject(config)(p),
      dispose = _.close(),
      healthCheck = _ ⇒ true
    )
  }

}