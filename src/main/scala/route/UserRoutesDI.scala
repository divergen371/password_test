package route

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all.*
import cats.effect.unsafe.implicits.global
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import spray.json.*
import util.{PasswordValidator, PasswordUtil}
import domain.*
import repo.UserRecord
import service.UserService
import common.{ApiResponse, JsonSupport}
import JsonSupport.given

/**
  * DI 可能な新 UserRoutes 実装（移行途中）。
  * まずは "POST /api/v1/users/hash" エンドポイントのみを EitherT + IO で実装。
  */
final class UserRoutesDI(userService: UserService[IO]) extends Directives with DefaultJsonProtocol:

  // --- リクエスト/レスポンス case class (既存と同一) ---
  case class Cred(name: String, password: String, role: Option[String] = None)
  case class Created(id: Int, message: String = "Created")
  case class PwChange(name: String, oldPassword: String, newPassword: String, confirm: String)

  given RootJsonFormat[Cred]    = jsonFormat3(Cred.apply)
  given RootJsonFormat[Created] = jsonFormat2(Created.apply)
  given RootJsonFormat[UserRecord] = jsonFormat5(UserRecord.apply)
  given RootJsonFormat[PwChange] = jsonFormat4(PwChange.apply)

  /** 新実装分のみのルート */
  val routes: Route =
    pathPrefix("api" / "v2") {
      pathPrefix("users") {
        post {
          path("hash") {
            entity(as[Cred]) { cred =>
              val program: EitherT[IO, DomainError, Int] = for
                _  <- EitherT.fromEither[IO](PasswordValidator.validate(cred.password).leftMap(_.head).toEither)
                id <- userService.create(cred.name, cred.password, cred.role.getOrElse("user")).leftWiden
              yield id

              onSuccess(program.value.unsafeToFuture()) {
                case Right(id) =>
                  complete(StatusCodes.Created, ApiResponse(success = true, data = Some(Created(id))))
                case Left(err @ PasswordTooShort) =>
                  complete(StatusCodes.BadRequest, ApiResponse[String](success = false, message = Some(err.message)))
                case Left(err @ UserAlreadyExists) =>
                  complete(StatusCodes.Conflict, ApiResponse[String](success = false, message = Some(err.message)))
                case Left(other) =>
                  complete(StatusCodes.InternalServerError, ApiResponse[String](success = false, message = Some(other.message)))
              }
            }
          } ~
          path("salt_hash") {
            entity(as[Cred]) { cred =>
              val program: EitherT[IO, DomainError, Int] = for
                _  <- EitherT.fromEither[IO](PasswordValidator.validate(cred.password).leftMap(_.head).toEither)
                id <- userService.createWithSalt(cred.name, cred.password, cred.role.getOrElse("user")).leftWiden
              yield id

              onSuccess(program.value.unsafeToFuture()) {
                case Right(id) =>
                  complete(StatusCodes.Created, ApiResponse(success = true, data = Some(Created(id))))
                case Left(err @ PasswordTooShort) =>
                  complete(StatusCodes.BadRequest, ApiResponse[String](success = false, message = Some(err.message)))
                case Left(err @ UserAlreadyExists) =>
                  complete(StatusCodes.Conflict, ApiResponse[String](success = false, message = Some(err.message)))
                case Left(other) =>
                  complete(StatusCodes.InternalServerError, ApiResponse[String](success = false, message = Some(other.message)))
              }
            }
          } ~
          path("change_password") {
            entity(as[PwChange]) { req =>
              val program: EitherT[IO, DomainError, Unit] = for
                _ <- userService.changePassword(req.name, req.oldPassword, req.newPassword, req.confirm)
              yield ()

              onSuccess(program.value.unsafeToFuture()) {
                case Right(_)  => complete(ApiResponse[String](success = true, message = Some("Password changed")))
                case Left(err) => complete(StatusCodes.BadRequest, ApiResponse[String](false, message=Some(err.message)))
              }
            }
          }
        }
      } ~
      (path("login") & post) {
        entity(as[Cred]) { cred =>
          val program: EitherT[IO, DomainError, Boolean] = for
            res <- userService.login(cred.name, cred.password)
          yield res

          onSuccess(program.value.unsafeToFuture()) {
            case Right(true)  => complete(ApiResponse[String](success = true, message = Some("Login Success")))
            case Right(false) => complete(StatusCodes.Unauthorized, ApiResponse[String](success = false, message = Some("Login Failed")))
            case Left(_)      => complete(StatusCodes.Unauthorized, ApiResponse[String](success = false, message = Some("Login Failed")))
          }
        }
      } ~
      pathPrefix("admin") {
        path("users") {
          (get | post) {
            entity(as[Cred]) { cred =>
              val program = userService.list(cred.name, cred.password)
              onSuccess(program.value.unsafeToFuture()) {
                case Right(list) => complete(ApiResponse(success = true, data = Some(list)))
                case Left(_)     => complete(StatusCodes.Unauthorized, ApiResponse[String](false, message=Some("Unauthorized")))
              }
            }
          }
        } ~
        pathPrefix("users" / IntNumber) { id =>
          delete {
            entity(as[Cred]) { cred =>
              val program = userService.delete(id, cred.name, cred.password)
              onSuccess(program.value.unsafeToFuture()) {
                case Right(_)  => complete(ApiResponse[String](true, message=Some("Deleted")))
                case Left(_)   => complete(StatusCodes.Unauthorized, ApiResponse[String](false, message=Some("Unauthorized or Not Found")))
              }
            }
          }
        }
      }
    }
