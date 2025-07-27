package domain

/** ビジネスロジック層のエラー ADT */
sealed trait DomainError {
  def message: String
}

case object PasswordTooShort extends DomainError {
  val message: String = "Password too short (>=8)"
}
