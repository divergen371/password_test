package util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PasswordUtilSpec extends AnyWordSpec with Matchers {

  "PasswordUtil" should {

    "hash password consistently" in {
      val password = "testpassword123"
      val hash1 = PasswordUtil.hashPassword(password)
      val hash2 = PasswordUtil.hashPassword(password)
      
      hash1 shouldEqual hash2
      hash1 should not be empty
    }

    "generate different salts for same password" in {
      val password = "testpassword123"
      val (hash1, salt1) = PasswordUtil.hashPasswordWithSalt(password)
      val (hash2, salt2) = PasswordUtil.hashPasswordWithSalt(password)
      
      salt1 should not equal salt2
      hash1 should not equal hash2
      salt1 should not be empty
      salt2 should not be empty
    }

    "verify password without salt" in {
      val password = "testpassword123"
      val hash = PasswordUtil.hashPassword(password)
      
      PasswordUtil.verify(password, hash, None) shouldBe true
      PasswordUtil.verify("wrongpassword", hash, None) shouldBe false
    }

    "verify password with salt" in {
      val password = "testpassword123"
      val (hash, salt) = PasswordUtil.hashPasswordWithSalt(password)
      
      PasswordUtil.verify(password, hash, Some(salt)) shouldBe true
      PasswordUtil.verify("wrongpassword", hash, Some(salt)) shouldBe false
      PasswordUtil.verify(password, hash, Some("wrongsalt")) shouldBe false
    }

    "validate password length" in {
      PasswordUtil.validate("short") shouldBe Some("Password too short (>=8)")
      PasswordUtil.validate("1234567") shouldBe Some("Password too short (>=8)")
      PasswordUtil.validate("12345678") shouldBe None
      PasswordUtil.validate("validpassword123") shouldBe None
    }

    "handle edge cases" in {
      PasswordUtil.validate("") shouldBe Some("Password too short (>=8)")
      PasswordUtil.validate("exactly8") shouldBe None
      
      // 空文字列のハッシュ化
      val emptyHash = PasswordUtil.hashPassword("")
      emptyHash should not be empty
      PasswordUtil.verify("", emptyHash, None) shouldBe true
    }

    "generate base64 encoded hashes" in {
      val password = "testpassword123"
      val hash = PasswordUtil.hashPassword(password)
      val (saltHash, salt) = PasswordUtil.hashPasswordWithSalt(password)
      
      // Base64文字列の基本的な検証
      hash should fullyMatch regex "[A-Za-z0-9+/]+=*"
      saltHash should fullyMatch regex "[A-Za-z0-9+/]+=*"
      salt should fullyMatch regex "[A-Za-z0-9+/]+=*"
    }
  }
}
