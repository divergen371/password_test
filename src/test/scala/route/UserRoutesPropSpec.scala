package route

import util.PropertySpec
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalacheck.Gen

/**
  * UserRoutes の主要 API をプロパティベースで検証する。
  */
class UserRoutesPropSpec extends PropertySpec with ScalatestRouteTest {

  // テスト対象ルート
  private val routes = UserRoutes.routes

  // 生成器 ---------------------------------------------------------
  /** 任意のユーザー名 (非空英字) */
  private val userName: Gen[String] =
    for {
      head <- Gen.alphaChar
      tail <- Gen.listOf(Gen.alphaNumChar)
    } yield (head :: tail).mkString

  /** 8〜32 文字の英数字パスワード */
  private val validPassword: Gen[String] =
    for {
      n   <- Gen.chooseNum(8, 32)
      str <- Gen.listOfN(n, Gen.alphaNumChar)
    } yield str.mkString

  /** 7 文字以下 (無効) パスワード */
  private val shortPassword: Gen[String] =
    for {
      n   <- Gen.chooseNum(1, 7)
      str <- Gen.listOfN(n, Gen.alphaNumChar)
    } yield str.mkString

  /** パスワード変更用に異なる新パスワードを生成する */
  private val newPassword: Gen[(String, String)] =
    for {
      old <- validPassword
      // new が old と異なるようにする
      diff <- validPassword.suchThat(_ != old)
    } yield (old, diff)

  // プロパティ -----------------------------------------------------

  "POST /users/hash" should {
    "accept any 8-32 character alphanum password" in
      forAll(userName, validPassword) { (name, pass) =>
        val json   = s"""{"name":"$name","password":"$pass"}"""
        val entity = HttpEntity(ContentTypes.`application/json`, json)

        Post("/users/hash", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }

    "reject passwords shorter than 8 characters" in
      forAll(userName, shortPassword) { (name, pass) =>
        val json   = s"""{"name":"$name","password":"$pass"}"""
        val entity = HttpEntity(ContentTypes.`application/json`, json)

        Post("/users/hash", entity) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }

    "reject duplicate user creation" in
      forAll(userName, validPassword) { (name, pass) =>
        val json   = s"""{"name":"$name","password":"$pass"}"""
        val entity = HttpEntity(ContentTypes.`application/json`, json)

        // 1回目: 作成成功
        Post("/users/hash", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }

        // 2回目: 同じデータで衝突
        Post("/users/hash", entity) ~> routes ~> check {
          status shouldBe StatusCodes.Conflict
        }
      }
  }

  "POST /login" should {
    "succeed with the exact credentials that were used at creation" in
      forAll(userName, validPassword) { (name, pass) =>
        val createJson   = s"""{"name":"$name","password":"$pass"}"""
        val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)
        Post("/users/hash", createEntity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }

        val loginEntity = HttpEntity(ContentTypes.`application/json`, createJson)
        Post("/login", loginEntity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }

    "always fail with unknown user or wrong password" in
      forAll(userName, validPassword) { (name, pass) =>
        val loginJson   = s"""{"name":"$name","password":"$pass"}"""
        val loginEntity = HttpEntity(ContentTypes.`application/json`, loginJson)

        Post("/login", loginEntity) ~> routes ~> check {
          status shouldBe StatusCodes.Unauthorized
        }
      }
  }

  "POST /users/change_password" should {
    "allow a user to change password then login with the new one" in
      forAll(userName, newPassword) { (name, pwPair) =>
        val (oldPw, newPw) = pwPair
        // ユーザ作成
        val createJson   = s"""{"name":"$name","password":"$oldPw"}"""
        val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)
        Post("/users/hash", createEntity) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }

        // パスワード変更
        val changeJson = s"""{"name":"$name","oldPassword":"$oldPw","newPassword":"$newPw","confirm":"$newPw"}"""
        val changeEntity = HttpEntity(ContentTypes.`application/json`, changeJson)
        Post("/users/change_password", changeEntity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }

        // 新PW でログイン
        val loginJson   = s"""{"name":"$name","password":"$newPw"}"""
        val loginEntity = HttpEntity(ContentTypes.`application/json`, loginJson)
        Post("/login", loginEntity) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }

    "reject when confirm does not match" in
      forAll(userName, validPassword, validPassword.suchThat(_ != "")) { (name, oldPw, otherPw) =>
        // enforce mismatch
        whenever(otherPw != oldPw) {
          val createJson   = s"""{"name":"$name","password":"$oldPw"}"""
          val createEntity = HttpEntity(ContentTypes.`application/json`, createJson)
          Post("/users/hash", createEntity) ~> routes ~> check { status shouldBe StatusCodes.Created }

          val changeJson   = s"""{"name":"$name","oldPassword":"$oldPw","newPassword":"$otherPw","confirm":"mismatch"}"""
          val changeEntity = HttpEntity(ContentTypes.`application/json`, changeJson)
          Post("/users/change_password", changeEntity) ~> routes ~> check {
            status shouldBe StatusCodes.BadRequest
          }
        }
      }
  }
}
