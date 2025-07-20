package model

case class User(
  id: Int,
  name: String,
  password: String,
  salt: Option[String] = None,
  role: String = "user"
)