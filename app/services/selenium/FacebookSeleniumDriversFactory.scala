package services.selenium

import com.machinepublishers.jbrowserdriver.JBrowserDriver
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.github.andrebeat.pool.{Pool, ReferenceType}
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration._
import utils._

object FacebookSeleniumDriversFactory extends LazyLogging {

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
      capacity = config.getInt("selenium.maxTotal"),
      factory = () ⇒ activateObject(config)(AbstractSeleniumDriversFactory.create(config)),
      referenceType = ReferenceType.Strong,
      maxIdleTime = 5.days,
      reset = (p) ⇒ {
        logger.info("Resetting instance")
        p.asInstanceOf[JBrowserDriver].reset()
        activateObject(config)(p)
      },
      dispose = p ⇒ {
        logger.info("Disposing instant")
        p.close()
      },
      healthCheck = _ ⇒ true
    )
  }
}