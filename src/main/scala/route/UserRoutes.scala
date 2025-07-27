package route

import model.User
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import Directives.*
import spray.json.*
import util.PasswordUtil
import org.apache.pekko.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshaller}
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller

import scala.collection.mutable

object UserRoutes extends DefaultJsonProtocol {

  // ------------ case classes (public) ------------
  case class Cred(name: String, password: String, role: Option[String] = None)

  case class Created(id: Int, message: String = "Created")

  case class Response(message: String)

  case class PwChange(name: String, oldPassword: String, newPassword: String, confirm: String)

  case class ApiResponse[T](success: Boolean, data: Option[T] = None, message: Option[String] = None)

  // ------------ JSON フォーマット ---------------
  given RootJsonFormat[Cred]    = jsonFormat3(Cred.apply)
  given RootJsonFormat[Created] = jsonFormat2(Created.apply)
  given RootJsonFormat[Response]= jsonFormat1(Response.apply)
  given RootJsonFormat[User]    = jsonFormat5(User.apply)
  given RootJsonFormat[PwChange]= jsonFormat4(PwChange.apply)

  import spray.json.DefaultJsonProtocol.*
  given apiResponseFormat[T](using fmt: JsonFormat[T]): RootJsonFormat[ApiResponse[T]] = new RootJsonFormat[ApiResponse[T]] {
    override def write(obj: ApiResponse[T]): JsValue = JsObject(
      "success" -> JsBoolean(obj.success),
      "data"    -> obj.data.map(fmt.write).getOrElse(JsNull),
      "message" -> obj.message.map(JsString.apply).getOrElse(JsNull)
    )

    override def read(json: JsValue): ApiResponse[T] = json.asJsObject.getFields("success", "data", "message") match
      case Seq(JsBoolean(s), d, m) =>
        ApiResponse(s, if d == JsNull then None else Some(fmt.read(d)), m match
          case JsString(str) => Some(str)
          case _ => None)
      case _ => deserializationError("ApiResponse expected")
  }

  // RootJsonFormat[String] が存在しないため補完
  given RootJsonFormat[String] = new RootJsonFormat[String] {
    def write(str: String): JsValue = JsString(str)
    def read(value: JsValue): String = value match
      case JsString(s) => s
      case _           => deserializationError("String expected")
  }

  // ApiResponse marshaller to HTTP response
  given apiRespRespMarshaller[T](using RootJsonFormat[ApiResponse[T]]): ToResponseMarshaller[ApiResponse[T]] =
    sprayJsonMarshaller[ApiResponse[T]]

  // ------------ ルーティング --------------------
  val routes: Route =
    pathPrefix("api" / "v1") {
      path("test") {
        get {
          import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Test route works!"))
        }
      } ~
      (path("login") & post) {
        entity(as[Cred]) { cred =>
          users.values.find(_.name == cred.name) match
            case Some(u) if PasswordUtil.verify(cred.password, u.password, u.salt) =>
              complete(ApiResponse[String](success = true, message = Some("Login Success")))
            case _ =>
              complete(StatusCodes.Unauthorized, ApiResponse[String](success = false, message = Some("Login Failed")))
        }
      } ~
      pathPrefix("users") {
        post {
          path("hash") {
            entity(as[Cred]) { cred =>
              PasswordUtil.validate(cred.password) match
                case Some(err) => complete(StatusCodes.BadRequest, ApiResponse[String](success=false,message=Some(err)))
                case None =>
                  whenUniqueUser(cred.name) {
                    seqId += 1
                    val hashed = PasswordUtil.hashPassword(cred.password)
                    users += seqId -> User(seqId, cred.name, hashed, None, cred.role.getOrElse("user"))
                    complete(StatusCodes.Created, ApiResponse(success = true, data = Some(Created(seqId))))
                  }
            }
          } ~
          path("salt_hash") {
            entity(as[Cred]) { cred =>
              PasswordUtil.validate(cred.password) match
                case Some(err) => complete(StatusCodes.BadRequest, ApiResponse[String](success=false,message=Some(err)))
                case None =>
                  whenUniqueUser(cred.name) {
                    seqId += 1
                    val (hashed, salt) = PasswordUtil.hashPasswordWithSalt(cred.password)
                    users += seqId -> User(seqId, cred.name, hashed, Some(salt), cred.role.getOrElse("user"))
                    complete(StatusCodes.Created, ApiResponse(success = true, data = Some(Created(seqId))))
                  }
            }
          } ~
          path("change_password") {
            entity(as[PwChange]) { req =>
              users.values.find(_.name == req.name) match
                case Some(u) if PasswordUtil.verify(req.oldPassword, u.password, u.salt) =>
                  if req.newPassword != req.confirm then
                    complete(StatusCodes.BadRequest, ApiResponse[String](false, message=Some("Password confirm mismatch")))
                  else PasswordUtil.validate(req.newPassword) match
                    case Some(err) => complete(StatusCodes.BadRequest, ApiResponse[String](success=false,message=Some(err)))
                    case None =>
                      if req.newPassword == req.oldPassword then
                        complete(StatusCodes.BadRequest, ApiResponse[String](false, message=Some("New password must differ from old")))
                      else
                        val (hashed, salt) = PasswordUtil.hashPasswordWithSalt(req.newPassword)
                        users.update(u.id, u.copy(password = hashed, salt = Some(salt)))
                        complete(ApiResponse[String](true, message = Some("Password changed")))
                case _ =>
                  complete(StatusCodes.Unauthorized, ApiResponse[String](false, message=Some("Old password incorrect")))
            }
          }
        }
      } ~
      pathPrefix("admin") {
        // ---- list users ----
        path("users") {
          // allow GET or POST (for backward compatibility)
          (get | post) {
            entity(as[Cred]) { cred =>
              adminAuth(cred) {
                complete(ApiResponse(success = true, data = Some(users.values.toList)))
              }
            }
          }
        } ~
        // ---- delete user ----
        pathPrefix("users" / IntNumber) { id =>
          delete {
            entity(as[Cred]) { cred =>
              adminAuth(cred) {
                if users.remove(id).isDefined then
                  complete(ApiResponse[String](true, message=Some(s"User $id deleted")))
                else
                  complete(StatusCodes.NotFound, ApiResponse[String](false, message=Some("User not found")))
              }
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
      complete(StatusCodes.Conflict, ApiResponse[String](false, message=Some("User already exists")))
    else inner

  /** Admin 認証: name/password が一致し role==admin なら inner を実行 */
  private def adminAuth(cred: Cred)(inner: => Route): Route =
    users.values.find(_.name == cred.name) match
      case Some(u) if u.role == "admin" && PasswordUtil.verify(cred.password, u.password, u.salt) => inner
      case _ => complete(StatusCodes.Forbidden, ApiResponse[String](false, message=Some("Admin privileges required")))
}