package common

import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import org.apache.pekko.http.scaladsl.model.MediaTypes
import spray.json.{DefaultJsonProtocol, JsBoolean, JsNull, JsObject, JsString, JsonFormat, RootJsonFormat, deserializationError, enrichAny}

/**
  * spray-json のフォーマット/Marshaller を集中管理
  */
object JsonSupport extends DefaultJsonProtocol {

  // --------------------- Primitive ---------------------
  // String など基本型は spray-json DefaultJsonProtocol が提供済み

  // --------------------- ApiResponse -------------------
  given apiResponseFormat[T](using fmt: JsonFormat[T]): RootJsonFormat[ApiResponse[T]] = new RootJsonFormat[ApiResponse[T]] {
    override def write(obj: ApiResponse[T]): JsObject = JsObject(
      "success" -> JsBoolean(obj.success),
      "data"    -> obj.data.map(fmt.write).getOrElse(JsNull),
      "message" -> obj.message.map(JsString.apply).getOrElse(JsNull)
    )

    override def read(json: spray.json.JsValue): ApiResponse[T] = json.asJsObject.getFields("success", "data", "message") match
      case Seq(spray.json.JsBoolean(s), d, m) =>
        ApiResponse(s,
          if d == JsNull then None else Some(fmt.read(d)),
          m match
            case JsString(str) => Some(str)
            case _             => None)
      case _ => deserializationError("ApiResponse expected")
  }

  // complete(value) 用の Response marshaller
  given apiResponseMarshaller[T](using RootJsonFormat[ApiResponse[T]]): ToResponseMarshaller[ApiResponse[T]] =
    Marshaller.stringMarshaller(MediaTypes.`application/json`).compose { (resp: ApiResponse[T]) =>
      resp.toJson.compactPrint
    }

  // --------------------- ProblemDetails ---------------
  import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
  /**
    * ProblemDetails を RFC7807 形式でエンコードする EntityMarshaller
    * complete(StatusCodes.Xxx, pd) で利用されるため ToEntityMarshaller が必要
    */
  given problemDetailsMarshaller(using RootJsonFormat[ProblemDetails]): ToEntityMarshaller[ProblemDetails] =
    Marshaller.stringMarshaller(MediaTypes.`application/problem+json`).compose { (details: ProblemDetails) =>
      details.toJson.compactPrint
    }
}
