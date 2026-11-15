# 開発ガイド

subfeed-browserはKotlinとAndroid標準View、GeckoViewで実装しています。ソースを取得し、リポジトリへ移動します。

```sh
git clone https://github.com/miyabisun/subfeed-browser.git
cd subfeed-browser
```

以降のコマンドは、このディレクトリで実行してください。
使い方は[README](../README.md)、アプリ自身の画面の設計は[DESIGN.md](../DESIGN.md)を参照してください。

## 構成

| 場所 | 内容 |
| --- | --- |
| `app/src/main/kotlin/` | アプリ本体。`Route.kt` がアドレスの振り分け、`Browser.kt` がエンジンと2つの画面、`MainActivity.kt` が表示 |
| `app/src/main/assets/extension/` | 内蔵の拡張。`feed.js` が一覧との取り決め、`youtube.css` がYouTubeから取り除く部分、`youtube.js` が再生の開始 |
| `app/src/test/` | JVMの単体テスト |
| `app/src/androidTest/` | 端末で動かすテストと、一覧の代わりをするページ（`assets/feed.html`） |
| `gradle/libs.versions.toml` | 依存の版。GeckoViewの版はここだけに書きます |
| `gradle.properties` | アプリの版（`appVersion`） |

YouTubeから取り除く部分を増やすには、`youtube.css` にセレクタを足します。
拡張はアプリの起動のたびに入れ直すので、拡張の `manifest.json` の版を上げる必要はありません。

## ビルドと検証

JDK 17、[Android SDK Command-line Tools](https://developer.android.com/studio#command-tools)、
Node.js（整形の検査がPrettierを使います）を用意します。
Gradle、Android Gradle Plugin、Kotlinの版はリポジトリの設定を使います。SDKの場所に合わせてパスを置き換えてください。

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses
sdkmanager 'platforms;android-37.2' 'build-tools;36.0.0' 'platform-tools'
./gradlew verify
```

`verify` は、整形の検査（ktlintとPrettier）、Android lint、単体テスト、debugビルドをまとめて実行します。
commitの前に通してください。整形は `./gradlew spotlessApply` で直せます。

debugビルドの出力は `app/build/outputs/apk/debug/app-debug.apk`、package IDは `dev.miyabisun.subfeedbrowser` です。
debugビルドでは、ページのコンソール出力がlogcatに出て、Firefoxのリモートデバッグを使えます。

GeckoView 160は、Android Gradle Plugin内蔵のKotlinより新しいkotlin-stdlibと、compileSdk 37.2を求めます。
そのため、ルートの `build.gradle.kts` でKotlinのGradle pluginを上げています。
GeckoViewを上げるときは、求められるKotlinとcompileSdkも確かめてください。

## 端末で動かすテスト

`app/src/androidTest/` のテストは、テストの中に立てたサーバーの擬似の一覧ページを使います。
ページは、本物の一覧が送るのと同じ合図を送るだけです。動画は本物のYouTubeを開くので、端末にネットワーク接続が必要です。
テストはどこにもログインしません。

arm64の実機をADBでつなぎ、次を実行します。複数の端末があるときは `ANDROID_SERIAL` で対象を指定してください。

```sh
./gradlew :app:connectedDebugAndroidTest
```

終了時に、端末からアプリが削除されます。ログイン済みのアプリが入った端末では実行しないでください。

確かめる内容は次のとおりです。

- 一覧の合図で動画を開き、戻ると一覧が読み込み直されず、同じ位置にあること。繰り返せること。
- YouTubeへの通常のリンクも、YouTubeの画面で開くこと。
- YouTube以外のアドレス、形の違うメッセージ、`intent:`・`vnd.youtube:` のリンクでは何も起きないこと。
- 新しいタブで開くほかのサイトへのリンクが、既定のブラウザへ渡ること。
- 初回にURLを尋ね、開けないアドレスを断ること。
- 一覧に届かないときに、再試行とURLの変更を出すこと。

再生が始まること、YouTubeから取り除いた部分、全画面と回転は、実機の画面で確かめます。

## ADBでのインストール

PCへ[Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)を導入し、
端末の開発者向けオプションでUSBデバッグかワイヤレスデバッグを有効にして、PCとつなぎます。
[リリース](https://github.com/miyabisun/subfeed-browser/releases/latest)からAPKと `SHA256SUMS` を同じフォルダーへ保存します。
以下の `X.Y.Z` は取得した版、`DEVICE_SERIAL` は `adb devices -l` で確かめた端末の識別子へ置き換えてください。

```sh
sha256sum -c SHA256SUMS
adb -s DEVICE_SERIAL install -r subfeed-browser-vX.Y.Z.apk
adb -s DEVICE_SERIAL shell am start -n dev.miyabisun.subfeedbrowser/.MainActivity
```

`-r` は、同じ署名の既存のアプリを、データ（ログインと設定）を保ったまま更新します。
署名の違うAPK（別のマシンでビルドしたものなど）からは上書きできません。
アプリを削除するとログインも消えるので、署名の違う版へは入れ替えずに使い続けてください。
リリースの `release-info.json` に、ソースのcommit、APKのSHA-256、署名証明書のSHA-256、GeckoViewの版があります。

## リリース

リリースのAPKはCIでは作りません。署名鍵をリポジトリやCIへ置かないためです。
タグのソースから手元でビルドし、手元のAndroidのdebug鍵（`~/.android/debug.keystore`）で署名します。
リリースのAPKは、debugビルドと違ってADBから中身を調べられないビルド（`release`）です。

1. `gradle.properties` の `appVersion` を新しい版にしてcommitし、`vX.Y.Z` のタグを付けてpushします。
   `versionCode` は 主×1000000＋副×1000＋patch で、`appVersion` から作られます。
2. タグをcheckoutした状態で、リリースノートのファイルを渡して実行します。

   ```sh
   scripts/release.sh /path/to/notes.md
   ```

スクリプトは、タグと `appVersion` の一致と作業ツリーに変更が無いことを確かめ、`verify` とreleaseビルドを実行します。
その後、次の3つを付けてGitHub Releaseを作ります。

- `subfeed-browser-vX.Y.Z.apk`
- `SHA256SUMS`
- `release-info.json`

同じ署名で更新を続けるには、同じマシンの同じdebug鍵でビルドしてください。
