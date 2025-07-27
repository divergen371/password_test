package repo

import cats.data.EitherT
import cats.effect.IO
import domain.{DomainError, UserAlreadyExists, UserNotFound}
import util.PasswordUtil

/** ユーザ永続化層の抽象 */
trait UserRepo[F[_]] {
  def create(name: String, password: String, role: String = "user"): EitherT[F, DomainError, Int]
  def createWithSalt(name: String, password: String, role: String = "user"): EitherT[F, DomainError, Int]
  def findByName(name: String): EitherT[F, DomainError, Option[UserRecord]]
  def updatePassword(name: String, hash: String, salt: Option[String]): EitherT[F, DomainError, Unit]
  def list(): F[List[UserRecord]]
  def delete(id: Int): EitherT[F, DomainError, Unit]
}

case class UserRecord(id: Int, name: String, password: String, salt: Option[String], role: String)
