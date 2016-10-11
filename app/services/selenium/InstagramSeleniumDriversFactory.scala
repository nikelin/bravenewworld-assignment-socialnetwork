package services.selenium

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import io.github.andrebeat.pool.{Pool, ReferenceType}
import org.apache.commons.pool2.PooledObject
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, WebDriver}

import scala.concurrent.duration._
import utils._

object InstagramSeleniumDriversFactory {

  def create(config: Config): Pool[WebDriver] = {
    Pool(
      capacity = 5,
      factory = AbstractSeleniumDriversFactory.create(config),
      referenceType = ReferenceType.Strong,
      maxIdleTime = 1.hour,
      reset = (p: WebDriver) ⇒ {
        "linkedin:clear cookies" timing { p.manage().deleteAllCookies() }

        "initial" timing { p.get("https://www.instagram.com/?hl=en") }

        val email =
          "email" timing { new WebDriverWait(p, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='email']"))) }

        val pass =
          "password" timing { new WebDriverWait(p, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='password']"))) }

        val loginForm = p.findElement(By.cssSelector("form"))

        "email:sendKeys" timing { email.sendKeys(config.getString("oauth.instagram.botAccount.username")) }
        "email:password" timing { pass.sendKeys(config.getString("oauth.instagram.botAccount.password")) }

        "form:submit" timing { loginForm.submit() }
      },
      dispose = _.close(),
      healthCheck = _ ⇒ true
    )
  }

}