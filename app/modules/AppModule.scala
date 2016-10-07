package modules

import com.google.inject.{AbstractModule, TypeLiteral}
import com.google.inject.name.Names
import com.typesafe.config.{Config, ConfigFactory}
import dal.DataAccessManager
import dal.impl.DummyDataAccessManager
import org.apache.commons.pool2.ObjectPool
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}
import org.openqa.selenium.WebDriver
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._
import services.impl.RelationshipValueEstimatorImpl
import services.RelationshipValueEstimator
import services.oauth.SocialServiceConnectors
import services.periodic.{PeriodicScheduler, SchedulerActor}
import services.selenium.{AbstractSeleniumDriversFactory, FacebookSeleniumDriversFactory, InstagramSeleniumDriversFactory, LinkedinSeleniumDriversFactory}

object AppModule {
  val linkedinSeleniumDriversPool = "linkedin"
  val facebookSeleniumDriversPool = "facebook"
  val instagramSeleniumDriversPool = "instagram"
}

class AppModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    val config = ConfigFactory.load

    val driversPoolConfig = new GenericObjectPoolConfig()
    driversPoolConfig.setMaxTotal(config.getInt("selenium.maxTotal"))
    driversPoolConfig.setMaxWaitMillis(1.minute.toMillis)

    bind(new TypeLiteral[ObjectPool[WebDriver]] {})
      .annotatedWith(Names.named(AppModule.linkedinSeleniumDriversPool))
      .toInstance(new GenericObjectPool(new LinkedinSeleniumDriversFactory(config), driversPoolConfig))

    bind(new TypeLiteral[ObjectPool[WebDriver]] {})
      .annotatedWith(Names.named(AppModule.facebookSeleniumDriversPool))
      .toInstance(new GenericObjectPool(new FacebookSeleniumDriversFactory(config), driversPoolConfig))

    bind(new TypeLiteral[ObjectPool[WebDriver]] {})
      .annotatedWith(Names.named(AppModule.instagramSeleniumDriversPool))
      .toInstance(new GenericObjectPool(new InstagramSeleniumDriversFactory(config), driversPoolConfig))

    bind(classOf[Config]).toInstance(config)
    bind(classOf[DataAccessManager]).to(classOf[DummyDataAccessManager])
    bind(classOf[SocialServiceConnectors])

    bind(classOf[RelationshipValueEstimator]).to(classOf[RelationshipValueEstimatorImpl])

    bindActor[SchedulerActor]("scheduler-actor")
    bind(classOf[PeriodicScheduler]).asEagerSingleton()
  }
}
