package service

import cats.data.EitherT
import cats.effect.IO
import domain.*

/**
  * User-related use-cases (Tagless Final 対応版)。
  * 今フェーズではメソッドは未実装でスケルトンのみ用意。
  */
trait UserService[F[_]]:
  def placeholder: F[Unit]
