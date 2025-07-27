package common

import cats.effect.IO

/**
  * 共通型エイリアス。将来的に `F[_]` を抽象化したい場合はここを拡張する。
  */
 type F[A] = IO[A]
