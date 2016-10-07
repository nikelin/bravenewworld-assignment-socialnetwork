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

    def asString: String
  }

  object UserAccountId {
    object FacebookId {
      sealed trait FacebookIdType
      object FacebookIdType {
        case object AppScopedId extends FacebookIdType
        case object Username extends FacebookIdType
        case object GlobalScopedId extends FacebookIdType
      }
    }

    case class FacebookId(value: String, tpe: FacebookId.FacebookIdType) extends UserAccountId {
      override val serviceType = ServiceType.Facebook

      override def asString: String = value
    }

    case class LinkedinId(value: String) extends UserAccountId {
      override val serviceType = ServiceType.Linkedin
      override def asString: String = value
    }

    case class InstagramId(value: String) extends UserAccountId {
      override val serviceType = ServiceType.Instagram
      override def asString: String = value
    }
  }

  sealed trait DomainEntity

  case class Id[T](value: String) extends DomainEntity

  case class UserSession(user: Id[User], accessToken: String, created: ZonedDateTime) extends DomainEntity

  case class User(createdOn: ZonedDateTime) extends DomainEntity

  case class Person(internalId: UserAccountId) extends DomainEntity

  case class UserProfile(name: String, photo: Option[String]) extends DomainEntity

  sealed trait PersonAttributeType {
    type Value <: PersonAttributeValue
  }

  object PersonAttributeType {
    case object Photo extends PersonAttributeType {
      override type Value = PersonAttributeValue.Photo
    }

    case object Text extends PersonAttributeType {
      override type Value = PersonAttributeValue.Text
    }

    case object Interest extends PersonAttributeType {
      override type Value = PersonAttributeValue.Interest
    }

    case object WorkExperience extends PersonAttributeType {
      override type Value = PersonAttributeValue.WorkExperience
    }
  }

  sealed trait PersonProfileField {
    val asString: String
  }

  object PersonProfileField {
    case object FollowedByCount extends PersonProfileField {
      override val asString = "followedByCount"
    }

    case object FollowsCount extends PersonProfileField {
      override val asString = "followsCount"
    }

    case object UserName extends PersonProfileField {
      override val asString = "userName"
    }

    case object GlobalScopedId extends PersonProfileField {
      override val asString = "globalScopedId"
    }

    case object ContentCreatedCount extends PersonProfileField {
      override val asString = "contentCreatedCount"
    }

    case object Name extends PersonProfileField {
      override val asString = "name"
    }
  }

  sealed trait PersonAttributeValue
  object PersonAttributeValue {
    case class Photo(value: String) extends PersonAttributeValue
    case class Text(name: String, value: String) extends PersonAttributeValue
    case class Interest(value: String) extends PersonAttributeValue
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
