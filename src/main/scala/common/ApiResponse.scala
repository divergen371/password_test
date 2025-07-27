package common

/**
  * 共通レスポンスエンベロープ。
  *
  * @param success 成功フラグ
  * @param data    実際の値（成功時）
  * @param message メッセージ（任意）
  */
final case class ApiResponse[T](success: Boolean, data: Option[T] = None, message: Option[String] = None)
