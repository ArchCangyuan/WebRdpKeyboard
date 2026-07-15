# Web RDP Keyboard

一个面向 Android 的轻量 Cloudflare Web RDP WebView wrapper，解决远程桌面画布无法主动唤起安卓输入法的问题。

## 功能

- 首次启动填写任意 `https://` Cloudflare Web RDP / Access 地址。
- 底部常驻键盘工具栏；软键盘弹出后工具栏自动位于键盘上方。
- `⌨` 按钮通过原生输入代理强制唤起 Android IME。
- `Shift` 和 `Ctrl` 可锁定，可与输入法提交的字符组合使用。
- `Esc`、`Tab`、`Enter`、退格和方向键直接发送到 WebView 中的 RDP 客户端。
- WebView Cookie 持久化，Cloudflare Access 登录无需每次重新输入。
- 仅允许 HTTPS，不绕过 TLS 或 Safe Browsing 警告。

## 使用

1. 从 GitHub Actions 的 `web-rdp-keyboard-debug` artifact 下载 APK 并安装。
2. 首次启动填写 Cloudflare Web RDP 地址并完成 Access 登录。
3. 进入远程桌面后点 `⌨` 唤出输入法。
4. 需要组合键时先点亮 `Ctrl` 或 `Shift`，再在输入法上输入；再次点击解锁。
5. 点 `⚙` 可更换地址。

> 不同 Web RDP 前端对 Unicode/组合输入的处理可能不同。应用优先发送 Android 原生 `KeyEvent`，对无法映射成物理键的字符再发送标准 DOM composition/input/keyboard 事件。

## 本地构建

需要 JDK 17 与 Android SDK 35：

```bash
./gradlew lintDebug assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## GitHub Actions

推送到 `main`、创建 Pull Request 或手动运行 workflow 都会执行 lint 并构建 debug APK。构建结果保留 30 天。
