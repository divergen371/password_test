package common

import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, deserializationError}

/**
  * RFC 7807 Problem Details オブジェクト
  * @param `type`   問題種別の識別 URI
  * @param title    人間向け短い説明
  * @param status   HTTP ステータスコード
  * @param detail   追加詳細説明
  * @param instance 問題が発生した具体的 URI
  */
final case class ProblemDetails(`type`: String,
                                title: String,
                                status: Int,
                                detail: Option[String] = None,
                                instance: Option[String] = None)

object ProblemDetails extends DefaultJsonProtocol {
  given RootJsonFormat[ProblemDetails] = new RootJsonFormat[ProblemDetails] {
    override def write(p: ProblemDetails): JsValue = JsObject(
      "type"     -> JsString(p.`type`),
      "title"    -> JsString(p.title),
      "status"   -> JsNumber(p.status),
      "detail"   -> p.detail.map(JsString.apply).getOrElse(spray.json.JsNull),
      "instance" -> p.instance.map(JsString.apply).getOrElse(spray.json.JsNull)
    )

    override def read(json: JsValue): ProblemDetails = json.asJsObject.getFields("type", "title", "status", "detail", "instance") match
      case Seq(JsString(t), JsString(ttl), JsNumber(st), det, inst) =>
        ProblemDetails(t, ttl, st.toIntExact,
          detail   = det match
            case JsString(s) => Some(s)
            case _           => None,
          instance = inst match
            case JsString(s) => Some(s)
            case _           => None
        )
      case _ => deserializationError("ProblemDetails expected")
  }
}
