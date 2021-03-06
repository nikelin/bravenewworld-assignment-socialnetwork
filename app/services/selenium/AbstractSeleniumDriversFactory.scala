package services.selenium

import java.io.File
import javax.inject.{Inject, Singleton}

import com.machinepublishers.jbrowserdriver.UserAgent.Family
import com.machinepublishers.jbrowserdriver._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration._
import scala.util.control.NonFatal

object AbstractSeleniumDriversFactory {
  val DesktopAgent = UserAgent.CHROME

  val TouchAgent = new UserAgent(Family.WEBKIT,
    "Google Inc.",
    "iPhone",
    "CPU iPhone 0S 9_1 like Mac OS X",
    "5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1")

  private val headersTmp = new java.util.LinkedHashMap[String, String]
  headersTmp.put("Host", RequestHeaders.DYNAMIC_HEADER)
  headersTmp.put("Connection", "keep-alive")
  headersTmp.put("Accept", "text/html,application/xhtml+xml,application/json,application/xml;q=0.9,image/webp,*/*;q=0.8")
  headersTmp.put("Upgrade-Insecure-Requests", "1")
  headersTmp.put("User-Agent", RequestHeaders.DYNAMIC_HEADER)
  headersTmp.put("Referer", RequestHeaders.DYNAMIC_HEADER)
  headersTmp.put("Accept-Encoding", "gzip, deflate, sdch")
  headersTmp.put("Accept-Language", "en-US,en;q=0.8")
  headersTmp.put("Cookie", RequestHeaders.DYNAMIC_HEADER)

  val ChromeHeaders = new RequestHeaders(headersTmp)

  def create(settings: Config)(): WebDriver = {
    new JBrowserDriver(
      Settings.builder()
        .cache(true)
        .userDataDirectory(new File(settings.getString("selenium.userDataPath")))
        .cacheDir(new File(settings.getString("selenium.userCachePath")))
        .maxRouteConnections(8)
        .requestHeaders(RequestHeaders.CHROME)
        .userAgent(DesktopAgent)
        .headless(true)
        .quickRender(true)
        .hostnameVerification(false)
        .processes(1)
        .logWarnings(false)
        .blockAds(true)
        .timezone(Timezone.AMERICA_NEWYORK)
        .build()
    )
  }
}