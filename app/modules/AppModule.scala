package modules

import com.google.inject.AbstractModule
import com.typesafe.config.{Config, ConfigFactory}
import dal.DataAccessManager
import dal.impl.DummyDataAccessManager
import play.api.libs.concurrent.AkkaGuiceSupport
import services.impl.RelationshipValueEstimatorImpl
import services.{RelationshipValueEstimator, SocialServiceConnectors}
import services.periodic.{PeriodicScheduler, SchedulerActor}

class AppModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bind(classOf[Config]).toInstance(ConfigFactory.load)
    bind(classOf[DataAccessManager]).to(classOf[DummyDataAccessManager])
    bind(classOf[SocialServiceConnectors])
    
    bind(classOf[RelationshipValueEstimator]).to(classOf[RelationshipValueEstimatorImpl])

    bindActor[SchedulerActor]("scheduler-actor")
    bind(classOf[PeriodicScheduler]).asEagerSingleton()
  }
}
