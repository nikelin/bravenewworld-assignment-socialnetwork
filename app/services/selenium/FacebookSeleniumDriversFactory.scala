package services.selenium

import java.util.concurrent.TimeUnit

import com.machinepublishers.jbrowserdriver.JBrowserDriver
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.github.andrebeat.pool.{Pool, ReferenceType}
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration._
import utils._

import scala.concurrent.{Await, ExecutionContext, Future}

object FacebookSeleniumDriversFactory extends LazyLogging {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def activateObject(config: Config)(p: WebDriver): WebDriver = {
    "linkedin:clear cookies" timing { p.manage().deleteAllCookies() }

    "initial" timing { Await.ready(Future(p.get("https://m.facebook.com")), 5.seconds) }

    val email =
      "email" timing { Await.result(Future(new WebDriverWait(p, 15.seconds.toMillis).until(
        ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='m_login_email']")))), 15.seconds) }

    val pass =
      "password" timing { Await.result(Future(new WebDriverWait(p, 15.seconds.toMillis).until(
        ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-sigil='password-plain-text-toggle-input']")))),
        15.seconds) }

    val loginForm = p.findElement(By.cssSelector("form[data-sigil='m_login_form']"))

    "email:sendKeys" timing { email.sendKeys(config.getString("oauth.facebook.botAccount.email")) }
    "email:password" timing { pass.sendKeys(config.getString("oauth.facebook.botAccount.password")) }

    "form:submit" timing { loginForm.submit() }

    p
  }

  def create(config: Config): Pool[WebDriver] = {
    Pool(
      capacity = config.getInt("selenium.maxTotal"),
      maxIdleTime = Duration(config.getDuration("selenium.idleTimeout").toMillis, TimeUnit.MILLISECONDS),
      factory = () ⇒ activateObject(config)(AbstractSeleniumDriversFactory.create(config)),
      referenceType = ReferenceType.Strong,
      reset = (p) ⇒ {
        try {
          p.asInstanceOf[JBrowserDriver].reset()
          activateObject(config)(p)
        } catch {
          case e: Throwable ⇒ logger.error("Activation failed", e)
        }
      },
      dispose = p ⇒ {
        try {
          p.asInstanceOf[JBrowserDriver].reset()
        } catch {
          case e: Throwable ⇒ logger.error("Dispose failed", e)
        }
      },
      healthCheck = p ⇒ p.getCurrentUrl != null // not sure about this condition, but it is the most that we can do
                                                // based on current JBrowserDriver public API
    )
  }
}