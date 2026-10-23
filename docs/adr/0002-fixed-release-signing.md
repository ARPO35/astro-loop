# ADR 0002：固定发布签名与 GitHub Actions 构建

- 状态：已采纳
- 日期：2026-10-23

## 背景

Android 只允许用同一签名证书覆盖安装。此前没有固定发布签名：本机与 CI 的 debug 签名各不相同，
换机器或重新构建后必须卸载旧版本，本地数据（存档、解锁、设置）随之丢失。上游的 F-Droid
签名密钥不在本仓库，无法用于本 fork。

需求是：给项目一个固定签名，后续更新保留数据；构建放到 GitHub Actions，不在本机安装
Android SDK / JDK。

## 决策

1. **一把固定的 RSA-4096 发布密钥**，PKCS12 格式，别名 `astroloop`，有效期 10000 天。
   密钥文件和密码只保存在两个地方：
   - 本机 `.signing/`（已 gitignore，需自行备份）；
   - GitHub Actions Secrets：
     - `ANDROID_KEYSTORE_BASE64`（PKCS12 的 Base64）
     - `ANDROID_KEYSTORE_PASSWORD`
     - `ANDROID_KEY_ALIAS`
     - `ANDROID_KEY_PASSWORD`

   仓库里不提交密钥或密码。

2. **GitHub Actions 构建并签名**。`.github/workflows/android.yml` 在 push 到 `main`、
   push `v*` 标签和手动触发时运行：
   - JDK 17 + Android SDK 36；
   - 运行 `testGameDebugUnitTest`；
   - 解码密钥、生成临时 `keystore.properties`；
   - 构建 `assembleGameRelease` 和 `assembleTuningRelease`；
   - 上传 APK artifact；
   - 标签构建创建 GitHub Release。

3. **versionCode 随构建递增**。工作流传入 `-PversionCodeOverride`（`200 + run_number`），
   保证每次产出的 APK 都能覆盖安装。`versionName` 标签构建用标签名，普通构建用
   `1.5.<run_number>`。

4. **本地构建仍可用**。复制 `keystore.properties.template` 为 `keystore.properties`，
   指向 `.signing/astroloop-release.p12`，并填写密码即可；`storeType=PKCS12`。

## 后果

- 后续 GitHub Actions 产出的 APK 可以互相覆盖安装，数据保留。
- 如果之前装的是上游官方包或其他签名（例如 debug 包），第一次切换到本签名必须卸载一次；
  之后不再需要。
- 丢失密钥 = 无法再覆盖更新。`.signing/` 必须备份；GitHub Secrets 无法读回。
- 公开仓库的 Secrets 不会暴露给 fork 的 PR；本工作流不监听 `pull_request`。
- 密钥在 CI 运行期间以临时文件存在，任务结束后 runner 销毁。
- 如果以后要发布到 F-Droid，需另配 reproducible build；本 ADR 只解决本 fork 的
  GitHub Releases/Obtainium 更新链路。

## 备选方案

- **提交 keystore 到仓库**：公开仓库等于公开私钥，任何人都能签同一包名的恶意更新。否决。
- **继续用 debug 签名**：不同机器/CI 的 debug key 不同，无法保证覆盖安装。否决。
- **使用上游 F-Droid 密钥**：私钥不在本仓库，无法获取。否决。
- **每次构建临时生成密钥**：每次签名都不同，等于每次都要卸载。否决。
