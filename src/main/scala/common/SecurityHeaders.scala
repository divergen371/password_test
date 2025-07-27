package common

import org.apache.pekko.http.scaladsl.model.headers.{RawHeader, `Strict-Transport-Security`}
import org.apache.pekko.http.scaladsl.server.Directives.respondWithHeaders
import org.apache.pekko.http.scaladsl.server.Route

object SecurityHeaders {

  private val headers = List(
    `Strict-Transport-Security`(maxAge = 63072000, includeSubDomains = true),
    RawHeader("X-Content-Type-Options", "nosniff"),
    RawHeader("X-Frame-Options", "deny"),
    RawHeader("Referrer-Policy", "no-referrer")
  )

  /** 指定ルートにセキュリティヘッダーを付与 */
  def apply(route: Route): Route = respondWithHeaders(headers)(route)
}
