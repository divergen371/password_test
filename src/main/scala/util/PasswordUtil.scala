package util

import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import domain.*

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
    * パスワード強度チェック。要件を満たせば Right(()), 満たさなければ Left(DomainError)
    */
  def validate(password: String): Either[DomainError, Unit] =
    if password.length < 8 then Left(PasswordTooShort) else Right(())

}
