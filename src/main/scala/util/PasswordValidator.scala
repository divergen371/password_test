package util

import cats.data.ValidatedNel
import cats.data.Validated.{invalidNel, validNel}
import domain.*

object PasswordValidator:
  /** 複合バリデーションを行い、成功なら ValidatedNel[DomainError, Unit] の Right 相当 */
  def validate(password: String): ValidatedNel[DomainError, Unit] =
    lengthRule(password)

  private def lengthRule(pw: String): ValidatedNel[DomainError, Unit] =
    if pw.length >= 8 then validNel(()) else invalidNel(PasswordTooShort)
