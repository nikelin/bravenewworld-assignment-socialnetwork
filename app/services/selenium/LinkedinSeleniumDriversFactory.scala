package services.selenium

import javax.inject.Inject

import com.machinepublishers.jbrowserdriver.UserAgent
import com.typesafe.config.Config
import org.apache.commons.pool2.PooledObject
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

class LinkedinSeleniumDriversFactory @Inject() (settings: Config) extends AbstractSeleniumDriversFactory(settings) {
  import utils._

  override val userAgent: UserAgent = AbstractSeleniumDriversFactory.DesktopAgent

  override def activateObject(p: PooledObject[WebDriver]): Unit = {
    "linkedin:clear cookies" timing { p.getObject.manage().deleteAllCookies() }

    "linkedin:initial" timing { p.getObject.get("https://www.linkedin.com/uas/login") }

    logger.info(s"Initial page loaded: ${p.getObject.getCurrentUrl}")

    val email =
      "linkedin:email" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.id("session_key-login"))) }

    val pass =
      "linkedin:password" timing { new WebDriverWait(p.getObject, 15).until(ExpectedConditions.presenceOfElementLocated(By.id("session_password-login"))) }

    val loginForm = p.getObject.findElement(By.id("login"))

    "linkedin:email:sendKeys" timing { email.sendKeys(settings.getString("oauth.linkedin.botAccount.email")) }
    "linkedin:email:password" timing { pass.sendKeys(settings.getString("oauth.linkedin.botAccount.password")) }

    "linkedin:form:submit" timing { loginForm.submit() }
  }

  override def passivateObject(p: PooledObject[WebDriver]): Unit = {
  }
}
