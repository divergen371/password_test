package util

import java.security.{MessageDigest, SecureRandom}
import java.util.Base64

object PasswordUtil {
  private val random = new SecureRandom()

  def hashPassword(password: String): String = sha256(password)

  def sha256(str: String): String =
    Base64.getEncoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(str.getBytes))

  def hashPasswordWithSalt(password: String): (String, String) =
    val saltBytes = new Array[Byte](16)
    random.nextBytes(saltBytes)

    val saltStr = Base64.getEncoder.encodeToString(saltBytes)
    val hashed = sha256(s"$saltStr$password")
    (hashed, saltStr)

  def verify(password: String, hash: String, saltOpt: Option[String]): Boolean =
    saltOpt match {
      case Some(salt) => sha256(s"$salt$password") == hash
      case None => sha256(password) == hash
    }

  /**
    * パスワード強度チェックに失敗した場合はエラーメッセージを返す。
    * 成功したら None。
    */
  def validate(password: String): Option[String] =
    if password.length < 8 then Some("Password too short (>=8)")
    else None
}
