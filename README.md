# パスワード管理API

Scala 3 + Pekko HTTP を使用したRESTful パスワード管理APIです。
学習目的

## 機能

### 基本機能

- **ユーザー登録**: 通常ハッシュ化とソルト付きハッシュ化に対応
- **ログイン認証**: ユーザー名・パスワードによる認証
- **パスワード変更**: 既存ユーザーのパスワード変更
- **パスワードバリデーション**: 8文字以上の制約
- **重複ユーザー防止**: ユーザー名の重複チェック

### 管理者機能

- **ユーザー一覧取得**: 全ユーザー情報の取得（admin権限必要）
- **ユーザー削除**: 指定ユーザーの削除（admin権限必要）

### セキュリティ機能

- **SHA-256ハッシュ化**: パスワードの安全な保存
- **ソルト対応**: レインボーテーブル攻撃対策
- **ロールベースアクセス制御**: admin/user権限の管理

## 技術スタック

- **Scala**: 3.3.3
- **Pekko HTTP**: 1.2.0（Akka HTTPのScala 3対応フォーク）
- **Pekko Actor**: 1.1.5
- **spray-json**: JSONシリアライゼーション
- **ScalaTest**: 3.2.19（テスト）
- **Logback**: ログ出力

## API エンドポイント

### 基本エンドポイント

#### `GET /test`

テスト用エンドポイント

```bash
curl http://localhost:8080/test
```

#### `POST /users/hash`

通常ハッシュ化でのユーザー登録

```bash
curl -X POST http://localhost:8080/users/hash \
  -H "Content-Type: application/json" \
  -d '{"name":"alice","password":"secret123","role":"user"}'
```

#### `POST /users/salt_hash`

ソルト付きハッシュ化でのユーザー登録

```bash
curl -X POST http://localhost:8080/users/salt_hash \
  -H "Content-Type: application/json" \
  -d '{"name":"bob","password":"secret123","role":"admin"}'
```

#### `POST /login`

ログイン認証

```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"name":"alice","password":"secret123"}'
```

#### `POST /users/change_password`

パスワード変更

```bash
curl -X POST http://localhost:8080/users/change_password \
  -H "Content-Type: application/json" \
  -d '{
    "name":"alice",
    "oldPassword":"secret123",
    "newPassword":"newsecret456",
    "confirm":"newsecret456"
  }'
```

### 管理者エンドポイント

#### `POST /admin/users`

全ユーザー一覧取得（admin権限必要）

```bash
curl -X POST http://localhost:8080/admin/users \
  -H "Content-Type: application/json" \
  -d '{"name":"admin","password":"adminpass"}'
```

#### `POST /admin/delete/{id}`

ユーザー削除（admin権限必要）

```bash
curl -X POST http://localhost:8080/admin/delete/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"admin","password":"adminpass"}'
```

## レスポンス例

### 成功レスポンス

```json
// ユーザー作成成功
{
  "id": 1,
  "message": "Created"
}

// ログイン成功
{
  "message": "Login Success"
}

// パスワード変更成功
{
  "message": "Password changed"
}
```

### エラーレスポンス

```json
// パスワード短すぎ (400 Bad Request)
{
  "message": "Password too short (>=8)"
}

// ユーザー重複 (409 Conflict)
{
  "message": "User already exists"
}

// ログイン失敗 (401 Unauthorized)
{
  "message": "Login Failed"
}

// 権限不足 (403 Forbidden)
{
  "message": "Admin privileges required"
}
```

## セットアップ

### 前提条件

- Java 11以上
- sbt 1.11.3以上

### 起動方法

```bash
# プロジェクトクローン
git clone <repository-url>
cd password_test

# 依存関係解決・コンパイル
sbt compile

# サーバー起動
sbt run
```

サーバーは `http://localhost:8080` で起動します。

### テスト実行

```bash
# 全テスト実行
sbt test

# 特定テストクラス実行
sbt "testOnly route.UserRoutesSpec"
sbt "testOnly util.PasswordUtilSpec"
```

## プロジェクト構成

```
src/
├── main/scala/
│   ├── main.scala              # アプリケーションエントリーポイント
│   ├── model/
│   │   └── User.scala          # ユーザーモデル
│   ├── route/
│   │   └── UserRoutes.scala    # HTTPルート定義
│   └── util/
│       └── PasswordUtil.scala  # パスワードユーティリティ
├── test/scala/
│   ├── route/
│   │   └── UserRoutesSpec.scala # ルートテスト
│   └── util/
│       └── PasswordUtilSpec.scala # ユーティリティテスト
└── main/resources/
    └── application.conf        # 設定ファイル
```

## 設計思想

### 関数型プログラミング

- 高階関数による共通処理の抽象化（`whenUniqueUser`, `adminAuth`）
- 不変データ構造の活用
- 副作用の局所化

### セキュリティ

- パスワードの平文保存を避ける
- ソルト使用によるレインボーテーブル攻撃対策
- ロールベースアクセス制御

### テスト駆動開発

- 全APIエンドポイントの包括的テスト
- ユニットテストとインテグレーションテストの組み合わせ
- 19個のテストケースで全機能をカバー

## 開発メモ

### 技術的な課題と解決

1. **Scala 3 + Akka HTTP互換性問題**
    - 解決: Pekko HTTP（Akka HTTPのScala 3対応フォーク）を採用

2. **JSON マーシャリング問題**
    - 解決: spray-json + Pekko HTTP Spray JSON を使用

3. **ルーティング問題のデバッグ**
    - 解決: 段階的なルート復旧とDebuggingDirectivesの活用

### 今後の拡張予定

- [ ] データベース永続化（H2, PostgreSQL等）
- [ ] JWT認証の実装
- [ ] パスワード強度チェックの拡張
- [ ] API レート制限
- [ ] Docker化
- [ ] CI/CD パイプライン

## ライセンス

MIT License
