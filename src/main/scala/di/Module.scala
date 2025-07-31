package di

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.softwaremill.macwire.*
import repo.InMemoryUserRepo
import service.{UserService, UserServiceImpl}
import route.UserRoutesDI

/**
  * MacWire による依存グラフ定義。
  * 将来 DB 実装に差し替える場合はここを変更するだけで良い。
  */
trait Module:
  // --- Infrastructure layer ---
  lazy val userRepo: InMemoryUserRepo = InMemoryUserRepo.empty.unsafeRunSync()

  // --- Service layer ---
  lazy val userService: UserService[IO] = wire[UserServiceImpl]

  // --- Interface layer ---
  lazy val userRoutes = wire[UserRoutesDI]
