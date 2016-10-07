package services.selenium

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import org.apache.commons.pool2.PooledObject
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, WebDriver}

@Singleton
class InstagramSeleniumDriversFactory @Inject()(settings: Config) extends AbstractSeleniumDriversFactory(settings) {
  import utils._

  override def activateObject(p: PooledObject[WebDriver]): Unit = {
    "linkedin:clear cookies" timing { p.getObject.manage().deleteAllCookies() }

    "initial" timing { p.getObject.get("https://www.instagram.com/?hl=en") }

    logger.info(s"Initial page loaded: ${p.getObject.getCurrentUrl}")

    val email =
      "email" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='email']"))) }

    val pass =
      "password" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='password']"))) }

    val loginForm = p.getObject.findElement(By.cssSelector("form"))

    "email:sendKeys" timing { email.sendKeys(settings.getString("oauth.instagram.botAccount.username")) }
    "email:password" timing { pass.sendKeys(settings.getString("oauth.instagram.botAccount.password")) }

    "form:submit" timing { loginForm.submit() }
  }

  override def passivateObject(p: PooledObject[WebDriver]): Unit = {
  }
}
