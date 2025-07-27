package common

import common.JsonSupport.given
import common.ProblemDetails
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.directives.RouteDirectives.complete
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, Directives}

/**
  * アプリ全体で利用する ExceptionHandler / RejectionHandler を提供
  */
object ExceptionHandlers extends Directives {

  // ----------------------- Exception -----------------------
  val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: IllegalArgumentException =>
      val pd = ProblemDetails("about:blank", "Validation error", StatusCodes.BadRequest.intValue, Some(ex.getMessage))
      complete(StatusCodes.BadRequest, pd)

    case ex: Throwable =>
      val pd = ProblemDetails("about:blank", "Internal Server Error", StatusCodes.InternalServerError.intValue, Some(ex.getMessage))
      complete(StatusCodes.InternalServerError, pd)
  }

  // ----------------------- Rejection -----------------------
  val rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handleNotFound {
      complete(StatusCodes.NotFound, ProblemDetails("about:blank", "Not Found", StatusCodes.NotFound.intValue))
    }
    .result()

  // ----------------------- Wrap helper --------------------
  def wrap(route: Route): Route =
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        route
      }
    }
}
