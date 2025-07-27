package repo

import cats.data.EitherT
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import domain.*
import util.PasswordUtil

/**
  * スレッドセーフな in-memory 実装。状態は Ref で保持。
  */
final class InMemoryUserRepo private (state: Ref[IO, InMemoryUserRepo.State]) extends UserRepo[IO]:

  def create(name: String, password: String, role: String): EitherT[IO, DomainError, Int] =
    EitherT {
      state.modify { st =>
        if st.users.values.exists(_.name == name) then
          (st, Left(UserAlreadyExists))
        else
          val id       = st.nextId
          val hashed   = PasswordUtil.hashPassword(password)
          val newUser  = UserRecord(id, name, hashed, None, role)
          val newState = st.copy(nextId = id + 1, users = st.users + (id -> newUser))
          (newState, Right(id))
      }
    }

  /** salt を伴うパスワード生成 */
  def createWithSalt(name: String, password: String, role: String): EitherT[IO, DomainError, Int] =
    EitherT {
      state.modify { st =>
        if st.users.values.exists(_.name == name) then
          (st, Left(UserAlreadyExists))
        else
          val id                = st.nextId
          val (hashed, salt)    = PasswordUtil.hashPasswordWithSalt(password)
          val newUser           = UserRecord(id, name, hashed, Some(salt), role)
          val newState          = st.copy(nextId = id + 1, users = st.users + (id -> newUser))
          (newState, Right(id))
      }
    }

  def findByName(name: String): EitherT[IO, DomainError, Option[UserRecord]] =
    EitherT.liftF {
      state.get.map(_.users.values.find(_.name == name))
    }

  def updatePassword(name: String, hash: String, salt: Option[String]): EitherT[IO, DomainError, Unit] =
    EitherT {
      state.modify { st =>
        st.users.values.find(_.name == name) match
          case None => (st, Left(UserNotFound))
          case Some(u) =>
            val updated = u.copy(password = hash, salt = salt)
            val newSt   = st.copy(users = st.users.updated(u.id, updated))
            (newSt, Right(()))
      }
    }

  def list(): IO[List[UserRecord]] = state.get.map(_.users.values.toList)

  def delete(id: Int): EitherT[IO, DomainError, Unit] =
    EitherT {
      state.modify { st =>
        if st.users.contains(id) then
          (st.copy(users = st.users - id), Right(()))
        else (st, Left(UserNotFound))
      }
    }

object InMemoryUserRepo:
  private final case class State(nextId: Int, users: Map[Int, UserRecord])

  def empty: IO[InMemoryUserRepo] =
    Ref.of[IO, State](State(1, Map.empty)).map(new InMemoryUserRepo(_))
