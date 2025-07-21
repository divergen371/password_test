package util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/**
  * 共通テストヘルパ。
  * 他のテストクラスで `with PropertySpec` を継承すると、
  *   - AnyWordSpec スタイル
  *   - Matchers DSL
  *   - Future の完了を扱う ScalaFutures
  *   - ScalaCheckPropertyChecks によるプロパティベーステスト
  * が利用できる。
  */
trait PropertySpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with ScalaCheckPropertyChecks
