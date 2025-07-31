package route

import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import util.PropertySpec
import repo.InMemoryUserRepo
import service.UserServiceImpl
import spray.json.*
import cats.effect.unsafe.implicits.global

/**
  * v2 API の主要フローをプロパティベースで検証する。  
  * - 任意の name/password/role でユーザ作成 → ログイン成功を保証  
  * - 同じ name を 2 回作成すると 400 を返す
  */
class UserRoutesV2PropSpec
    extends PropertySpec
    with ScalaCheckPropertyChecks
    with ScalatestRouteTest
    with DefaultJsonProtocol:

  private def newRoutes =
    val repo = InMemoryUserRepo.empty.unsafeRunSync()
    val service = UserServiceImpl(repo)
    new UserRoutesDI(service).routes

  case class Cred(name: String, password: String, role: Option[String] = None)
  given RootJsonFormat[Cred] = jsonFormat3(Cred.apply)

  private val nameGen: Gen[String] = Gen.alphaStr.suchThat(_.nonEmpty)
  private val passwordGen: Gen[String] = Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)
  private val roleGen: Gen[Option[String]] = Gen.option(Gen.oneOf("user", "admin"))

  "User creation and login" should {
    "succeed for arbitrary credentials" in {
      forAll(nameGen, passwordGen, roleGen) { (name, pass, role) =>
        val routes = newRoutes
        // create user
        val credJson  = Cred(name, pass, role).toJson.compactPrint
        Post("/api/v2/users/hash", HttpEntity(ContentTypes.`application/json`, credJson)) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
        // login
        val loginJson = Cred(name, pass).toJson.compactPrint
        Post("/api/v2/login", HttpEntity(ContentTypes.`application/json`, loginJson)) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] should include("success\":true")
        }
      }
    }
  }

  "Duplicate user creation" should {
    "return Conflict on second attempt" in {
      forAll(nameGen, passwordGen) { (name, pass) =>
        val routes = newRoutes
        val credJson  = Cred(name, pass).toJson.compactPrint
        Post("/api/v2/users/hash", HttpEntity(ContentTypes.`application/json`, credJson)) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
        // second attempt same name
        Post("/api/v2/users/hash", HttpEntity(ContentTypes.`application/json`, credJson)) ~> routes ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
    }
  }

  "Password change flow" should {
    "allow changing password and login succeeds with new one" in {
      forAll(nameGen, passwordGen, passwordGen) { (name, oldPass, newPassRaw) =>
        whenever(oldPass != newPassRaw) {
          val newPass = if newPassRaw == oldPass then newPassRaw + "x" else newPassRaw
          val routes = newRoutes

          // create user
          val credJson = Cred(name, oldPass).toJson.compactPrint
          Post("/api/v2/users/hash", HttpEntity(ContentTypes.`application/json`, credJson)) ~> routes ~> check {
            status shouldBe StatusCodes.Created
          }

          // change password
          case class PwChange(name: String, oldPassword: String, newPassword: String, confirm: String)
          given RootJsonFormat[PwChange] = jsonFormat4(PwChange.apply)
          val pwJson = PwChange(name, oldPass, newPass, newPass).toJson.compactPrint
          Post("/api/v2/users/change_password", HttpEntity(ContentTypes.`application/json`, pwJson)) ~> routes ~> check {
            status shouldBe StatusCodes.OK
          }

          // login with new password succeeds
          val loginJson = Cred(name, newPass).toJson.compactPrint
          Post("/api/v2/login", HttpEntity(ContentTypes.`application/json`, loginJson)) ~> routes ~> check {
            status shouldBe StatusCodes.OK
          }
        }
      }
    }
  }

  "Admin list/delete" should {
    "list created users and allow deletion" in {
      forAll(nameGen, passwordGen) { (normalName, pass) =>
        val routes = newRoutes

        // create admin user
        val adminCred = Cred("admin", "adminPass123", Some("admin"))
        Post("/api/v2/users/hash", HttpEntity(ContentTypes.`application/json`, adminCred.toJson.compactPrint)) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }

        // create normal user
        Post("/api/v2/users/hash", HttpEntity(ContentTypes.`application/json`, Cred(normalName, pass).toJson.compactPrint)) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }

        // list users via admin
        val adminAuthJson = adminCred.copy(role=None).toJson.compactPrint
        Post("/api/v2/admin/users", HttpEntity(ContentTypes.`application/json`, adminAuthJson)) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }

        // delete normal user (id = 2)
        Delete(s"/api/v2/admin/users/2", HttpEntity(ContentTypes.`application/json`, adminAuthJson)) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }
