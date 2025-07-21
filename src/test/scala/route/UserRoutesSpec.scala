package route

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json.*

class UserRoutesSpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  // テスト用のActorSystem
  lazy val testKit: ActorTestKit = ActorTestKit()
  
  // UserRoutesのインスタンスを取得
  val userRoutes: Route = UserRoutes.routes

  override def cleanUp(): Unit = {
    testKit.shutdownTestKit()
  }

  "UserRoutes" should {

    "return test message for GET /test" in {
      Get("/test") ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Test route works!"
      }
    }

    "create user with hash endpoint" in {
      val userJson = """{"name":"testuser","password":"testpass123"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)

      Post("/users/hash", entity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
        val response = responseAs[String]
        response should include("Created")
        response should include("\"id\":")
      }
    }

    "reject short password" in {
      val userJson = """{"name":"testuser2","password":"short"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)

      Post("/users/hash", entity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = responseAs[String]
        response should include("Password too short")
      }
    }

    "reject duplicate user" in {
      val userJson = """{"name":"duplicate","password":"validpass123"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)

      // 最初のユーザー作成
      Post("/users/hash", entity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      // 重複ユーザー作成を試行
      Post("/users/hash", entity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Conflict
        val response = responseAs[String]
        response should include("User already exists")
      }
    }

    "create user with salt_hash endpoint" in {
      val userJson = """{"name":"saltuser","password":"saltpass123"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)

      Post("/users/salt_hash", entity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
        val response = responseAs[String]
        response should include("Created")
      }
    }

    "login successfully with correct credentials" in {
      val createJson = """{"name":"loginuser","password":"loginpass123"}"""
      val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)

      // ユーザー作成
      Post("/users/hash", createEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      // ログイン試行
      val loginJson = """{"name":"loginuser","password":"loginpass123"}"""
      val loginEntity = HttpEntity(ContentTypes.`application/json`, loginJson)

      Post("/login", loginEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[String]
        response should include("Login Success")
      }
    }

    "fail login with incorrect credentials" in {
      val loginJson = """{"name":"nonexistent","password":"wrongpass"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, loginJson)

      Post("/login", entity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        val response = responseAs[String]
        response should include("Login Failed")
      }
    }

    "change password successfully" in {
      val createJson = """{"name":"changeuser","password":"oldpass123"}"""
      val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)

      // ユーザー作成
      Post("/users/hash", createEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      // パスワード変更
      val changeJson = """{"name":"changeuser","oldPassword":"oldpass123","newPassword":"newpass456","confirm":"newpass456"}"""
      val changeEntity = HttpEntity(ContentTypes.`application/json`, changeJson)

      Post("/users/change_password", changeEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[String]
        response should include("Password changed")
      }

      // 新しいパスワードでログイン確認
      val loginJson = """{"name":"changeuser","password":"newpass456"}"""
      val loginEntity = HttpEntity(ContentTypes.`application/json`, loginJson)

      Post("/login", loginEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[String]
        response should include("Login Success")
      }
    }

    "reject password change with mismatched confirm" in {
      val createJson = """{"name":"mismatchuser","password":"oldpass123"}"""
      val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)

      Post("/users/hash", createEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      val changeJson = """{"name":"mismatchuser","oldPassword":"oldpass123","newPassword":"newpass456","confirm":"different456"}"""
      val changeEntity = HttpEntity(ContentTypes.`application/json`, changeJson)

      Post("/users/change_password", changeEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.BadRequest
        val response = responseAs[String]
        response should include("Password confirm mismatch")
      }
    }

    "admin can list users" in {
      // 管理者作成
      val adminJson = """{"name":"admin","password":"adminpass123","role":"admin"}"""
      val adminEntity = HttpEntity(ContentTypes.`application/json`, adminJson)

      Post("/users/hash", adminEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      // 一般ユーザー作成
      val userJson = """{"name":"normaluser","password":"userpass123"}"""
      val userEntity = HttpEntity(ContentTypes.`application/json`, userJson)

      Post("/users/hash", userEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      // ユーザー一覧取得
      val listJson = """{"name":"admin","password":"adminpass123"}"""
      val listEntity = HttpEntity(ContentTypes.`application/json`, listJson)

      Post("/admin/users", listEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[String]
        response should include("admin")
        response should include("normaluser")
      }
    }

    "non-admin cannot list users" in {
      val userJson = """{"name":"regularuser","password":"userpass123"}"""
      val userEntity = HttpEntity(ContentTypes.`application/json`, userJson)

      Post("/users/hash", userEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      val listJson = """{"name":"regularuser","password":"userpass123"}"""
      val listEntity = HttpEntity(ContentTypes.`application/json`, listJson)

      Post("/admin/users", listEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
        val response = responseAs[String]
        response should include("Admin privileges required")
      }
    }

    "admin can delete users" in {
      // 管理者作成
      val adminJson = """{"name":"admin2","password":"adminpass123","role":"admin"}"""
      val adminEntity = HttpEntity(ContentTypes.`application/json`, adminJson)

      Post("/users/hash", adminEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
      }

      // 削除対象ユーザー作成
      val targetJson = """{"name":"deleteuser","password":"userpass123"}"""
      val targetEntity = HttpEntity(ContentTypes.`application/json`, targetJson)

      Post("/users/hash", targetEntity) ~> userRoutes ~> check {
        status shouldEqual StatusCodes.Created
        val response = responseAs[String]
        val userId = response.substring(response.indexOf("\"id\":") + 5, response.indexOf(","))

        // ユーザー削除
        val deleteJson = """{"name":"admin2","password":"adminpass123"}"""
        val deleteEntity = HttpEntity(ContentTypes.`application/json`, deleteJson)

        Post(s"/admin/delete/$userId", deleteEntity) ~> userRoutes ~> check {
          status shouldEqual StatusCodes.OK
          val deleteResponse = responseAs[String]
          deleteResponse should include(s"User $userId deleted")
        }
      }
    }
  }
}
