package services.selenium

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import io.github.andrebeat.pool.{Pool, ReferenceType}
import org.apache.commons.pool2.PooledObject
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration._

import utils._

object FacebookSeleniumDriversFactory {

  def activateObject(config: Config)(p: WebDriver): WebDriver = {
    "linkedin:clear cookies" timing { p.manage().deleteAllCookies() }

    "initial" timing { p.get("https://m.facebook.com") }

    val email =
      "email" timing { new WebDriverWait(p, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='m_login_email']"))) }

    val pass =
      "password" timing { new WebDriverWait(p, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='password-plain-text-toggle-input']"))) }

    val loginForm = p.findElement(By.cssSelector("form[data-sigil='m_login_form']"))

    "email:sendKeys" timing { email.sendKeys(config.getString("oauth.facebook.botAccount.email")) }
    "email:password" timing { pass.sendKeys(config.getString("oauth.facebook.botAccount.password")) }

    "form:submit" timing { loginForm.submit() }

    p
  }

  def create(config: Config): Pool[WebDriver] = {
    Pool(
      capacity = 5,
      factory = () ⇒ activateObject(config)(AbstractSeleniumDriversFactory.create(config)),
      referenceType = ReferenceType.Strong,
      maxIdleTime = 1.hour,
      reset = (p) ⇒ activateObject(config)(p),
      dispose = _.close(),
      healthCheck = _ ⇒ true
    )
  }
}