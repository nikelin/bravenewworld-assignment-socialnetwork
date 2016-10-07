package dal

import models._
import services.oauth.OAuth2Service

import scala.concurrent.{ExecutionContext, Future}

trait DataAccessManager {
  def computePersonLevel(person: Id[Person])(implicit ec: ExecutionContext): Future[Int]

  def findUserByPersonInternalId(internalId: UserAccountId)(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]]

  def findPersonById(personId: Id[Person])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[Person]]]

  def findPersonAttributesByPersonId(personId: Id[Person])(implicit ec: ExecutionContext): Future[Seq[PersonAttribute]]

  def createSession(accessToken: OAuth2Service.AccessToken, userId: Id[User])(implicit ec: ExecutionContext): Future[Id[UserSession]]

  def addUser(user: User, initialIdentity: Person)(implicit ec: ExecutionContext): Future[(Id[User], Id[Person])]

  def findRelationsByPersonId(personId: Id[Person])(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[Person]]]

  def findPersonsByUserId(userId: Id[User])(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[Person]]]

  def findAllUsers()(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[User]]]

  def findUserBySessionId(session: Id[UserSession])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]]

  def findUserByPersonId(personId: Id[Person])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]]

  def findPersonByInternalId(internalId: UserAccountId)(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[Person]]]

  def findSessionsByUserId(userId: Id[User])(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[UserSession]]]

  def findAllPersons()(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[Person]]]

  def findUserById(userId: Id[User])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]]

  def linkPerson(user: Id[User], person: Person)(implicit ec: ExecutionContext): Future[Id[Person]]

  def linkRelation(left: Id[Person], right: Id[Person])(implicit ec: ExecutionContext): Future[Unit]

  def updatePerson(id: Id[Person], person: Person)(implicit ec: ExecutionContext): Future[Id[Person]]

  def updatePersonAttributes(id: Id[Person], attributes: Seq[PersonAttribute])(implicit ec: ExecutionContext): Future[Seq[PersonAttribute]]

  def createPerson(person: Person)(implicit ec: ExecutionContext): Future[Id[Person]]

  def updateOrCreatePerson(person: Person)(implicit ec: ExecutionContext): Future[Id[Person]]

}
