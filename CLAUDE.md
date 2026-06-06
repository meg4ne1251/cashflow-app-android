# cashflow-app-android

Kotlin + Jetpack Compose の家計簿アプリ。バックエンドは `/home/megane/dev/cashflow-app`。

## 開発フロー

- **コード編集**: このVM上で行う
- **動作確認・UI検証**: ローカルのMacで Android CLI を使って行う

VM環境はGUIがないためエミュレータが起動できない。APKをビルドしてMacに持ち込むか、Macでビルドして確認する。

## Android CLI（Mac側で実行）

Android CLI（`android` コマンド）でエミュレータ操作・APKデプロイ・スクリーンショット取得ができる。

### よく使うコマンド

```bash
# エミュレータ
android emulator start medium_phone   # 起動
android emulator stop emulator-5554   # 停止

# ビルド＆デプロイ
./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/app-debug.apk

# 画面確認
android screen capture --output=screen.png --annotate

# Android Studio 起動中のみ
android studio render-compose-preview <file> <composable>
android studio version-lookup <artifact>
```

### UIを確認するときの手順（Mac側）

1. `android emulator start medium_phone`
2. `./gradlew assembleDebug`
3. `android run --apks=app/build/outputs/apk/debug/app-debug.apk`
4. `android screen capture --output=screen.png --annotate`

## ビルド

```bash
./gradlew assembleDebug        # デバッグビルド
./gradlew test                 # 単体テスト
./gradlew connectedAndroidTest # インストルメンテーションテスト（エミュレータ必要）
```
