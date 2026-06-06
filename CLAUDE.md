# cashflow-app-android

Kotlin + Jetpack Compose の家計簿アプリ。バックエンドは `/home/megane/dev/cashflow-app`。

## Android CLI

Android CLI（`android` コマンド）がインストール済み。エミュレータ操作・APKデプロイ・スクリーンショット取得をコマンドラインから行える。

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

### UIを確認するときの手順

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
