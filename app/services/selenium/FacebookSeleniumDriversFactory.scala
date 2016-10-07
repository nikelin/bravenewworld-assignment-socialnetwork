package services.selenium

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import org.apache.commons.pool2.PooledObject
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

@Singleton
class FacebookSeleniumDriversFactory @Inject()(settings: Config) extends AbstractSeleniumDriversFactory(settings) {
  import utils._

  override def activateObject(p: PooledObject[WebDriver]): Unit = {
    "linkedin:clear cookies" timing { p.getObject.manage().deleteAllCookies() }

    "initial" timing { p.getObject.get("https://m.facebook.com") }

    logger.info(s"Initial page loaded: ${p.getObject.getCurrentUrl}")

    val email =
      "email" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='m_login_email']"))) }

    val pass =
      "password" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='password-plain-text-toggle-input']"))) }

    val loginForm = p.getObject.findElement(By.cssSelector("form[data-sigil='m_login_form']"))

    "email:sendKeys" timing { email.sendKeys(settings.getString("oauth.facebook.botAccount.email")) }
    "email:password" timing { pass.sendKeys(settings.getString("oauth.facebook.botAccount.password")) }

    "form:submit" timing { loginForm.submit() }
  }

  override def passivateObject(p: PooledObject[WebDriver]): Unit = {
  }
}
