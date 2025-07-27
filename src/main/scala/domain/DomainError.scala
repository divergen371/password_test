package domain

/** ビジネスロジック層のエラー ADT */
sealed trait DomainError {
  def message: String
}

case object PasswordTooShort extends DomainError {
  val message: String = "Password too short (>=8)"
}

case object UserAlreadyExists extends DomainError {
  val message: String = "User already exists"
}

case object UserNotFound extends DomainError {
  val message: String = "User not found"
}
