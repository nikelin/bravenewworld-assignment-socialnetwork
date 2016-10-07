package services.impl

import javax.inject.{Inject, Singleton}

import dal.DataAccessManager
import models._
import services.RelationshipValueEstimator
import services.RelationshipValueEstimator.{Score, ScoreFactor}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object RelationshipValueEstimatorImpl {

  final val defaultWeightFunction: RelationshipValueEstimator.WeightFunction = {
    case ScoreFactor.ShareNetwork(count) => count
    case ScoreFactor.ShareInterests(count) => count * 0.75
    case _ => 0
  }

}

@Singleton
class RelationshipValueEstimatorImpl @Inject() (dataAccessManager: DataAccessManager)
  extends RelationshipValueEstimator {
  import RelationshipValueEstimator._

  private final val weights: mutable.ListBuffer[RelationshipValueEstimator.WeightFunction] =
    mutable.ListBuffer(RelationshipValueEstimatorImpl.defaultWeightFunction)

  override def defineWeightFunction(weightFunction: RelationshipValueEstimator.WeightFunction): Unit = {
    weights += weightFunction
  }

  override def process(person: Id[Person], relations: Seq[Id[Person]])(implicit ec: ExecutionContext): Future[Seq[(Id[Person], Score)]] = {
    for {
      personNetwork <- dataAccessManager.findRelationsByPersonId(person)
      personAttributes <- dataAccessManager.findPersonAttributesByPersonId(person)
      results <- Future.sequence(relations map { relation =>
        for {
          relationNetwork <- dataAccessManager.findRelationsByPersonId(relation)
          relationAttributes <- dataAccessManager.findPersonAttributesByPersonId(relation)
        } yield {
          val sharedAttributes = personAttributes.intersect(relationAttributes)

          val sharedNetworkScore = ScoreFactor.ShareNetwork(personNetwork.map(_.entity.internalId.asString).intersect(relationNetwork.map(_.entity.internalId.asString)).size)
          val sharedInterests = ScoreFactor.ShareInterests(sharedAttributes.count(_.tpe == PersonAttributeType.Interest))
          val sharedWorkExperience = ScoreFactor.ShareWorkPlaces(sharedAttributes.count(_.tpe == PersonAttributeType.WorkExperience))

          val scores = Seq(sharedNetworkScore, sharedInterests, sharedWorkExperience)

          val calculatedScore = scores map { score =>
            (score, weights.foldLeft(0d)((l, r) => l + r(score)))
          }

          (relation, Score(calculatedScore, calculatedScore.map(v => v._2).sum))
        }
      })
    } yield results
  }

}
