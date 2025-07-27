import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.directives.DebuggingDirectives
import org.apache.pekko.http.scaladsl.server.directives.LoggingMagnet
import route.UserRoutesDI
import repo.InMemoryUserRepo
import cats.effect.unsafe.implicits.global

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

@main def run(): Unit =
  given system: ActorSystem[Nothing] = ActorSystem(org.apache.pekko.actor.typed.scaladsl.Behaviors.empty, "api-system")

  given ExecutionContextExecutor = summon[ActorSystem[Nothing]].executionContext

  // より詳細なログ設定
  val repo  = InMemoryUserRepo.empty.unsafeRunSync()
  val routes = new UserRoutesDI(repo).routes

  val loggedRoutes = logRequest("Request") {
    logResult("Response") {
      routes
    }
  }
  
  val binding = Http().newServerAt("0.0.0.0", 8080).bind(loggedRoutes)
  println("Server started at http://localhost:8080/")
  
  // Keep server running
  try {
    Thread.sleep(Long.MaxValue)
  } catch {
    case _: InterruptedException =>
      println("Server shutting down...")
      binding.flatMap(_.unbind()).onComplete(_ => summon[ActorSystem[Nothing]].terminate())
  }