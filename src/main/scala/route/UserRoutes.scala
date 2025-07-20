package route

import model.User
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import Directives.*
import spray.json.*
import util.PasswordUtil

import scala.collection.mutable

object UserRoutes extends DefaultJsonProtocol {

  // ------------ case classes (public) ------------
  case class Cred(name: String, password: String, role: Option[String] = None)

  case class Created(id: Int, message: String = "Created")

  case class Response(message: String)

  case class PwChange(name: String, oldPassword: String, newPassword: String, confirm: String)

  // ------------ JSON フォーマット ---------------
  given RootJsonFormat[Cred]    = jsonFormat3(Cred.apply)
  given RootJsonFormat[Created] = jsonFormat2(Created.apply)
  given RootJsonFormat[Response]= jsonFormat1(Response.apply)
  given RootJsonFormat[User]    = jsonFormat5(User.apply)
  given RootJsonFormat[PwChange]= jsonFormat4(PwChange.apply)

  // ------------ ルーティング --------------------
  val routes: Route =
    path("test") {
      get {
        complete("Test route works!")
      }
    } ~
    (path("login") & post) {
      entity(as[Cred]) { cred =>
        users.values.find(_.name == cred.name) match
          case Some(u) if PasswordUtil.verify(cred.password, u.password, u.salt) =>
            complete(Response("Login Success"))
          case _ =>
            complete(StatusCodes.Unauthorized, Response("Login Failed"))
      }
    } ~
    pathPrefix("users") {
      post {
        path("hash") {
          entity(as[Cred]) { cred =>
            PasswordUtil.validate(cred.password) match
              case Some(err) => complete(StatusCodes.BadRequest, Response(err))
              case None =>
                whenUniqueUser(cred.name) {
                  seqId += 1
                  val hashed = PasswordUtil.hashPassword(cred.password)
                  users += seqId -> User(seqId, cred.name, hashed, None, cred.role.getOrElse("user"))
                  complete(StatusCodes.Created, Created(seqId))
                }
          }
        } ~
        path("salt_hash") {
          entity(as[Cred]) { cred =>
            PasswordUtil.validate(cred.password) match
              case Some(err) => complete(StatusCodes.BadRequest, Response(err))
              case None =>
                whenUniqueUser(cred.name) {
                  seqId += 1
                  val (hashed, salt) = PasswordUtil.hashPasswordWithSalt(cred.password)
                  users += seqId -> User(seqId, cred.name, hashed, Some(salt), cred.role.getOrElse("user"))
                  complete(StatusCodes.Created, Created(seqId))
                }
          }
        } ~
        path("change_password") {
          entity(as[PwChange]) { req =>
            users.values.find(_.name == req.name) match
              case Some(u) if PasswordUtil.verify(req.oldPassword, u.password, u.salt) =>
                if req.newPassword != req.confirm then
                  complete(StatusCodes.BadRequest, Response("Password confirm mismatch"))
                else PasswordUtil.validate(req.newPassword) match
                  case Some(err) => complete(StatusCodes.BadRequest, Response(err))
                  case None =>
                    if req.newPassword == req.oldPassword then
                      complete(StatusCodes.BadRequest, Response("New password must differ from old"))
                    else
                      val (hashed, salt) = PasswordUtil.hashPasswordWithSalt(req.newPassword)
                      users.update(u.id, u.copy(password = hashed, salt = Some(salt)))
                      complete(Response("Password changed"))
              case _ =>
                complete(StatusCodes.Unauthorized, Response("Old password incorrect"))
          }
        }
      }
    } ~
    pathPrefix("admin") {
      post {
        path("users") {
          entity(as[Cred]) { cred =>
            adminAuth(cred) {
              complete(users.values.toList.toJson)
            }
          }
        } ~
        path("delete" / IntNumber) { id =>
          entity(as[Cred]) { cred =>
            adminAuth(cred) {
              if users.remove(id).isDefined then
                complete(Response(s"User $id deleted"))
              else
                complete(StatusCodes.NotFound, Response("User not found"))
            }
          }
        }
      }
    }

  // ------------ データ保存 ----------------------
  private val users = mutable.Map.empty[Int, User]
  private var seqId = 0

  /**
   * ユーザー名が未登録なら `inner` を実行し、重複していれば 409 Conflict を返す。
   * 副作用を包み込む高階関数として切り出し、呼び出し側を簡潔にする。
   */
  private def whenUniqueUser(name: String)(inner: => Route): Route =
    if users.values.exists(_.name == name) then
      complete(StatusCodes.Conflict, Response("User already exists"))
    else inner

  /** Admin 認証: name/password が一致し role==admin なら inner を実行 */
  private def adminAuth(cred: Cred)(inner: => Route): Route =
    users.values.find(_.name == cred.name) match
      case Some(u) if u.role == "admin" && PasswordUtil.verify(cred.password, u.password, u.salt) => inner
      case _ => complete(StatusCodes.Forbidden, Response("Admin privileges required"))
}