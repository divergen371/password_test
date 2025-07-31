package service

import cats.data.EitherT
import domain.*
import repo.UserRecord

/**
  * User-related use-cases (Tagless Final)。
  * F は IO や TestIO など。
  */
trait UserService[F[_]]:

  def create(name: String, password: String, role: String = "user"): EitherT[F, DomainError, Int]

  def createWithSalt(name: String, password: String, role: String = "user"): EitherT[F, DomainError, Int]

  def login(name: String, password: String): EitherT[F, DomainError, Boolean]

  def changePassword(name: String, oldPw: String, newPw: String, confirm: String): EitherT[F, DomainError, Unit]

  def list(authName: String, authPw: String): EitherT[F, DomainError, List[UserRecord]]

  def delete(id: Int, authName: String, authPw: String): EitherT[F, DomainError, Unit]
