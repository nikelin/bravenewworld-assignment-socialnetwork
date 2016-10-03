package services

import models.{Id, MaterializedEntity, Person}

import scala.concurrent.{ExecutionContext, Future}

object RelationshipValueEstimator {

  sealed trait ScoreFactor

  object ScoreFactor {
    case class ShareNetwork(friendsInCommon: Int) extends ScoreFactor
    case class ShareInterests(inCommon: Int) extends ScoreFactor
    case class ShareWorkPlaces(placesInCommon: Int) extends ScoreFactor
    case object IsCloseRelative extends ScoreFactor
    case class SizeOfNetwork(n: Int) extends ScoreFactor
    case class InfluentialPerson(averageLikes: Double, averageReposts: Double) extends ScoreFactor
  }

  case class Score(factor: Seq[(ScoreFactor, Double)], totalValue: Double)

  type WeightFunction = PartialFunction[ScoreFactor, Double]
}

trait RelationshipValueEstimator {

  def defineWeightFunction(weightFunction: RelationshipValueEstimator.WeightFunction): Unit

  def process(person: Id[Person], relations: Seq[Id[Person]])(implicit ec: ExecutionContext): Future[Seq[(Id[Person], RelationshipValueEstimator.Score)]]

}
