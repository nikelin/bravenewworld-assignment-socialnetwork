import java.time.ZonedDateTime

package object models {

  sealed trait ServiceType {
    val asString: String
  }

  object ServiceType {
    case object Facebook extends ServiceType {
      override val asString = "facebook"
    }

    case object Linkedin extends ServiceType {
      override val asString = "linkedin"
    }

    case object Instagram extends ServiceType {
      override val asString = "instagram"
    }
  }

  sealed trait UserAccountId {
    val serviceType: ServiceType
  }

  object UserAccountId {
    case class FacebookId(value: String) extends UserAccountId {
      override val serviceType = ServiceType.Facebook
    }

    case class LinkedinId(value: String) extends UserAccountId {
      override val serviceType = ServiceType.Linkedin
    }

    case class InstagramId(value: String) extends UserAccountId {
      override val serviceType = ServiceType.Instagram
    }
  }

  sealed trait DomainEntity

  case class Id[T](value: String) extends DomainEntity

  case class UserSession(user: Id[User], accessToken: String, created: ZonedDateTime) extends DomainEntity

  case class User(createdOn: ZonedDateTime) extends DomainEntity

  case class Person(internalId: UserAccountId, profile: UserProfile) extends DomainEntity

  case class UserProfile(name: String, photo: Option[String]) extends DomainEntity

  sealed trait PersonAttributeType {
    type Value <: PersonAttributeValue
  }

  object PersonAttributeType {
    case object Photo extends PersonAttributeType {
      type Value = PersonAttributeValue.Photo
    }

    case object Text extends PersonAttributeType {
      type Value = PersonAttributeValue.Text
    }

    case object Interest extends PersonAttributeType {
      type Type = PersonAttributeValue.Interest
    }

    case object WorkExperience extends PersonAttributeType {
      type Type = PersonAttributeValue.WorkExperience
    }
  }

  sealed trait PersonAttributeValue
  object PersonAttributeValue {
    case class Photo(value: String) extends PersonAttributeValue
    case class Text(name: String, value: String) extends PersonAttributeValue
    case class Interest(value: DictionaryObject) extends PersonAttributeValue
    case class WorkExperience(company: Id[DictionaryObject], dateStart: ZonedDateTime, dateEnd: ZonedDateTime)
      extends PersonAttributeValue
    case class Relative(person: Id[Person]) extends PersonAttributeValue
  }

  case class DictionaryObject(tpe: String, data: String) extends DomainEntity

  object PersonAttribute{
    def apply(tpe: PersonAttributeType)(value: tpe.Value): PersonAttribute =
      new Impl[tpe.Value](tpe, value)

    case class Impl[S <: PersonAttributeType#Value](tpe: PersonAttributeType { type Value = S },
                               value: S)
      extends PersonAttribute {
      override def productPrefix = "SystemState"
    }
  }

  trait PersonAttribute {
    val tpe: PersonAttributeType
    val value: tpe.Value
  }

  case class MaterializedEntity[T](id: Id[T], entity: T) extends DomainEntity

}
