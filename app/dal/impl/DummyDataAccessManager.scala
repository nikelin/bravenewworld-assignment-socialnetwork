package dal.impl

import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Singleton

import dal.DataAccessManager
import models._
import services.OAuth2Service

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DummyDataAccessManager extends DataAccessManager {
  private final val users: mutable.ListBuffer[MaterializedEntity[User]] = mutable.ListBuffer()
  private final val persons: mutable.ListBuffer[MaterializedEntity[Person]] = mutable.ListBuffer()
  private final val personAttributes: mutable.Map[Id[Person], Seq[PersonAttribute]] = mutable.Map()
  private final val sessions: mutable.ListBuffer[MaterializedEntity[UserSession]] = mutable.ListBuffer()

  private final val personToPersonIndex: mutable.Map[Id[Person], Seq[Id[Person]]] = new mutable.HashMap()
  private final val userToPersonsIndex: mutable.Map[Id[User], Seq[Id[Person]]] = new mutable.HashMap()


  override def findUserByPersonInternalId(internalId: UserAccountId)(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]] = {
    Future {
      persons.find(_.entity.internalId == internalId)
        .flatMap(p => userToPersonsIndex.find(_._2.contains(p.id)).map(_._1) )
        .flatMap(u => users.find(_.id.value == u.value))
    }
  }

  override def createSession(accessToken: OAuth2Service.AccessToken, userId: Id[User])(implicit ec: ExecutionContext): Future[Id[UserSession]] =
    Future {
      this.synchronized {
        val record = materialize(UserSession(userId, accessToken.value, ZonedDateTime.now))
        sessions += record
        record.id
      }
    }

  override def findUserBySessionId(session: Id[UserSession])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]] =
    Future {
      for {
        session <- sessions.find(_.id.value == session.value)
        user <- users.find(_.id.value == session.entity.user.value)
      } yield user
    }

  override def findUserById(userId: Id[User])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]] =
    Future { users.find(_.id.value == userId.value) }

  override def addUser(user: User, initialSource: Person)(implicit ec: ExecutionContext): Future[Id[User]] = {
    Future {
      this.synchronized {
        val record = materialize(user)
        users += record
        record.id
      }
    } flatMap { recordId =>
      linkPerson(recordId, initialSource) map { _ =>
        recordId
      }
    }
  }

  override def linkPerson(user: Id[User], person: Person)(implicit ec: ExecutionContext): Future[Id[Person]] = {
    Future {
      val record: MaterializedEntity[Person] = if (!persons.exists(_.entity.internalId == person.internalId)) {
        val materialized = materialize(person)
        persons += materialized
        materialized
      } else persons.find(_.entity.internalId == person.internalId).get

      val userPersons = userToPersonsIndex.getOrElseUpdate(user, Seq())
      if (userPersons.count(_.value == record.id.value) == 0) {
        userToPersonsIndex.put(user, userPersons :+ record.id)
      }

      record.id
    }
  }

  override def findPersonByInternalId(internalId: UserAccountId)(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[Person]]] = {
    Future {
      persons.find(_.entity.internalId == internalId)
    }
  }

  override def linkRelation(left: Id[Person], right: Id[Person])(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      this.synchronized {
        val currentState = personToPersonIndex.getOrElseUpdate(left, Seq.empty)
        if ( currentState.count(_.value == right.value) == 0 ) {
          personToPersonIndex.put(left, personToPersonIndex.getOrElseUpdate(left, Seq.empty) :+ right)
        }
      }
    }
  }

  override def findAllUsers()(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[User]]] = {
    Future { users map identity }
  }

  override def findRelationsByPersonId(personId: Id[Person])(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[Person]]] = {
    Future {
      personToPersonIndex.getOrElseUpdate(personId, Seq.empty).flatMap( p => persons.find(_.id.value == p.value))
    }
  }

  override def findPersonsByUserId(userId: Id[User])(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[Person]]] = {
    Future {
      userToPersonsIndex.find(_._1.value == userId.value)
        .map(_._2)
        .map( xs => persons.filter(person => xs.exists(_.value == person.id.value)))
        .getOrElse(Seq.empty)
    }
  }

  override def findUserByPersonId(personId: Id[Person])(implicit ec: ExecutionContext): Future[Option[MaterializedEntity[User]]] = {
    Future {
      userToPersonsIndex.find(_._2.contains(personId)).flatMap(pair => users.find(_.id.value == pair._1.value))
    }
  }

  override def findSessionsByUserId(userId: Id[User])(implicit ec: ExecutionContext): Future[Seq[MaterializedEntity[UserSession]]] = {
    Future {
      sessions.filter(_.entity.user.value == userId.value)
    }
  }

  override def updateOrCreatePerson(person: Person)(implicit ec: ExecutionContext): Future[Id[Person]] = {
    this.findPersonByInternalId(person.internalId) flatMap {
      case Some(existsRecord) => this.updatePerson(existsRecord.id, person)
      case None => this.createPerson(person)
    }
  }

  override def updatePerson(id: Id[Person], person: Person)(implicit ec: ExecutionContext): Future[Id[Person]] = {
    Future {
      this.synchronized {
        this.persons.find(_.id.value == id.value)
          .map { p =>
            this.persons.update(this.persons.indexOf(p), p.copy(entity = person))
            p.id
          }
          .getOrElse(throw new IllegalArgumentException(s"record #${id.value} not found"))
      }
    }
  }

  override def createPerson(person: Person)(implicit ec: ExecutionContext): Future[Id[Person]] = {
    Future {
      this.synchronized {
        this.persons.find(_.entity.internalId == person.internalId)
          .map(_.id)
          .getOrElse {
            val record = materialize(person)
            this.persons += record
            record.id
          }
      }
    }
  }

  override def findPersonAttributesByPersonId(personId: Id[Person])(implicit ec: ExecutionContext): Future[Seq[PersonAttribute]] = {
    Future {
      personAttributes.getOrElse(personId, Seq.empty)
    }
  }

  private def materialize[T](entity: T): MaterializedEntity[T] = {
    MaterializedEntity(Id[T](UUID.randomUUID().toString), entity)
  }
}
