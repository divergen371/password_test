package service

import cats.effect.IO
import repo.UserRepo

/** 仮実装：何もしない UseCase。今後ビジネスロジックを実装予定 */
final class UserServiceImpl(repo: UserRepo[IO]) extends UserService[IO]:
  override def placeholder: IO[Unit] = IO.unit
