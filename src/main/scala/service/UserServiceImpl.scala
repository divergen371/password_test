package service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all.*

import domain.*
import repo.{UserRepo, UserRecord}
import util.{PasswordValidator, PasswordUtil}

/**
  * 現行 UserRoutes のビジネスロジックを Service 層へ移植。
  * ルート層は純粋に DTO ↔ Domain 変換＋ HTTP マッピングのみにする予定。
  */
final class UserServiceImpl(repo: UserRepo[IO]) extends UserService[IO]:

  override def create(name: String, password: String, role: String): EitherT[IO, DomainError, Int] =
    for
      _  <- EitherT.fromEither[IO](PasswordValidator.validate(password).leftMap(_.head).toEither)
      id <- repo.create(name, password, role)
    yield id

  override def createWithSalt(name: String, password: String, role: String): EitherT[IO, DomainError, Int] =
    for
      _  <- EitherT.fromEither[IO](PasswordValidator.validate(password).leftMap(_.head).toEither)
      id <- repo.createWithSalt(name, password, role)
    yield id

  override def login(name: String, password: String): EitherT[IO, DomainError, Boolean] =
    for
      userOpt <- repo.findByName(name)
    yield userOpt.exists(u => PasswordUtil.verify(password, u.password, u.salt))

  override def changePassword(name: String, oldPw: String, newPw: String, confirm: String): EitherT[IO, DomainError, Unit] =
    for
      userOpt <- repo.findByName(name)
      user    <- EitherT.fromOption[IO](userOpt, UserNotFound)
      _       <- EitherT.cond[IO](PasswordUtil.verify(oldPw, user.password, user.salt), (), PasswordTooShort) // reuse error for simplicity
      _       <- EitherT.cond[IO](newPw == confirm, (), PasswordTooShort)
      _       <- EitherT.fromEither[IO](PasswordValidator.validate(newPw).leftMap(_.head).toEither)
      _       <- EitherT.cond[IO](newPw != oldPw, (), PasswordTooShort)
      (hashed, salt) = PasswordUtil.hashPasswordWithSalt(newPw)
      _       <- repo.updatePassword(name, hashed, Some(salt))
    yield ()

  private def authorizeAdmin(name: String, pw: String): EitherT[IO, DomainError, Unit] =
    for
      userOpt <- repo.findByName(name)
      user    <- EitherT.fromOption[IO](userOpt, UserNotFound)
      _       <- EitherT.cond[IO](user.role == "admin" && PasswordUtil.verify(pw, user.password, user.salt), (), UserNotFound)
    yield ()

  override def list(authName: String, authPw: String): EitherT[IO, DomainError, List[UserRecord]] =
    for
      _   <- authorizeAdmin(authName, authPw)
      lst <- EitherT.liftF(repo.list())
    yield lst

  override def delete(id: Int, authName: String, authPw: String): EitherT[IO, DomainError, Unit] =
    for
      _ <- authorizeAdmin(authName, authPw)
      _ <- repo.delete(id)
    yield ()

object UserServiceImpl:
  def apply(r: repo.UserRepo[IO]): UserServiceImpl = new UserServiceImpl(r)
