package route

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import repo.InMemoryUserRepo

class UserRoutesV2Spec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  /** 新しいルートを返す */
  private def newRoute: Route = {
    val repo = InMemoryUserRepo.empty.unsafeRunSync()
    new UserRoutesDI(repo).routes
  }

  "UserRoutesDI" should {
    "create user with hash endpoint" in {
      val routes = newRoute
      val userJson = """{"name":"testuser","password":"testpass123"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)
      Post("/api/v2/users/hash", entity) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[String] should include("Created")
      }
    }

    "reject short password" in {
      val routes = newRoute
      val userJson = """{"name":"shortpw","password":"short"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)
      Post("/api/v2/users/hash", entity) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] should include("Password too short")
      }
    }

    "reject duplicate user" in {
      val repo   = InMemoryUserRepo.empty.unsafeRunSync()
      val routes = new UserRoutesDI(repo).routes
      val userJson = """{"name":"dup","password":"validpass123"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)
      // first create
      Post("/api/v2/users/hash", entity) ~> routes ~> check { status shouldEqual StatusCodes.Created }
      // duplicate
      Post("/api/v2/users/hash", entity) ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
        responseAs[String] should include("User already exists")
      }
    }

    "create user with salt_hash endpoint" in {
      val routes = newRoute
      val userJson = """{"name":"saltuser","password":"saltpass123"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, userJson)
      Post("/api/v2/users/salt_hash", entity) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[String] should include("Created")
      }
    }

    "login success" in {
      val repo   = InMemoryUserRepo.empty.unsafeRunSync()
      val routes = new UserRoutesDI(repo).routes
      val createJson = """{"name":"loginuser","password":"loginpass123"}"""
      val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)
      Post("/api/v2/users/hash", createEntity) ~> routes ~> check { status shouldEqual StatusCodes.Created }

      val loginJson = """{"name":"loginuser","password":"loginpass123"}"""
      val loginEntity = HttpEntity(ContentTypes.`application/json`, loginJson)
      Post("/api/v2/login", loginEntity) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include("Login Success")
      }
    }

    "login fail" in {
      val routes = newRoute
      val loginJson = """{"name":"nouser","password":"wrong"}"""
      val entity = HttpEntity(ContentTypes.`application/json`, loginJson)
      Post("/api/v2/login", entity) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[String] should include("Login Failed")
      }
    }
  }
}
