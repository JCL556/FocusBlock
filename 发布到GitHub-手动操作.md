# 发布到 GitHub：手动操作步骤

仓库已经在本地初始化好了，分支叫 `main`，源码、README、LICENSE、发布说明都提交进去了。
安装包**不进仓库**（已加 `*.apk` 忽略），走 Release 附件上传——仓库网页上传单文件上限 25 MB，这个包 27 MB，塞不进去。

---

## 第一步：装 GitHub Desktop，登录

下载 <https://desktop.github.com> 装上，用你的 GitHub 账号登录。

## 第二步：把本地仓库加进去

1. 菜单 `File` → `Add local repository…`
2. 路径选：`D:\WorkBuddy1\2026-11-14-17-05-43\FocusBlock`
3. 点 `Add repository`。左侧会显示这个仓库，分支是 `main`

## 第三步：发布仓库

1. 右上角点 `Publish repository`
2. 填：
   - Name：`FocusBlock`
   - Description：`纯离线作息番茄钟，Android 11+，Kotlin + Compose`
   - 取消勾选 `Keep this code private`（要开源就公开）
3. 点 `Publish repository`，等进度条走完

到这一步代码就上线了。浏览器打开 `https://github.com/你的用户名/FocusBlock` 能看到文件。

## 第四步：建 Release，传安装包

1. 进仓库页面，右侧点 `Releases` → `Create a new release`（或直接 `https://github.com/你的用户名/FocusBlock/releases/new`）
2. 填：
   - `Choose a tag`：输入 `v1.7.7`，点 `Create new tag: v1.7.7 on publish`（这一步会打标签，务必在点发布前建）
   - Target：`main`
   - Release title：`FocusBlock 1.7.7`
3. 描述框里，把 `发布说明-v1.7.7.md` 的内容整段粘进去
4. 页面下方 `Attach binaries…` 区域，把 `FocusBlock-1.7.7.apk` 拖进去，等它传完（27 MB，慢点正常）
5. 点绿色 `Publish release`

完事。Release 页面会多一个下载链接，手机直接点这个链接就能装。

---

## 之后怎么更新

分两种，只有第二种要发新包。远端仓库是 `JCL556/FocusBlock`，分支 `main`。

### A　只改文档（README / CHANGELOG）

1. 改完存盘
2. GitHub Desktop 里左边看 `Changes`，右下角 Summary 写一句话（例如「修文案」）
3. 点 `Commit to main`，再点右上 `Push origin`
4. 网页刷新就生效，不用发 Release

### B　改了代码，要发新版本

1. `app/build.gradle.kts` 里 `versionCode` 加一，`versionName` 跟着改成 1.7.8（不升也能覆盖安装，但升了才分得清谁是谁）
2. 打包：`./gradlew assembleDebug`
3. 产物改名 `FocusBlock-1.7.8.apk`，放 `preview/` 或桌面，**不要放进仓库目录**
4. 文档跟上：README 顶部版本号、`## 版本历史` 加一行；`CHANGELOG.md` 顶部加一节
5. 同样 Commit → Push

### C　挂 Release（每次发新包都要做）

1. 网页打开仓库 → 右侧 `Releases` → `Draft a new release`
2. `Choose a tag` 输入 `v1.7.8`，点 `Create new tag: v1.7.8 on publish`；Target 选 `main`
3. 标题写 `FocusBlock 1.7.8`，说明框粘贴 `发布说明-v1.7.8.md` 的内容
4. 下方 `Attach binaries…` 把 apk 拖进去，等传完
5. 点绿色 `Publish release`

几条规矩：

- 一个版本一个 tag，别复用旧 tag
- 传错包了：进那个 Release 点 `Edit`，删掉附件重传，tag 不用动
- 别 force push，别改历史
- 旧 apk 永远不进仓库（`.gitignore` 已经排了 `*.apk`）

---

## 顺带的注意

- **别把 `local.properties` 传上去**，里面有你本机 SDK 路径。`.gitignore` 已经排掉了，GitHub Desktop 里看不到它就说明没问题。
- **别把 `FocusBlock-1.7.6.apk` 之类的旧包拖进仓库**，git 历史里删不掉，仓库会一直背着几十 MB。
- **APK 是调试签名的**。以后要用正式密钥签名，先卸载旧版再装，否则装不上（签名不一致）。
- 仓库公开后别人能直接 clone 编译，README 里的构建要求（JDK 17 / SDK 35）不要删。

## 不想用 GitHub Desktop 的话

命令行也行，前提是先去 GitHub 网页建一个空仓库，然后在 `FocusBlock` 目录下执行：

```bash
git remote add origin https://github.com/你的用户名/FocusBlock.git
git push -u origin main
```

用 HTTPS 推的时候要输账号密码，GitHub 已经不支持密码了，得去
`Settings` → `Developer settings` → `Personal access tokens` → `Tokens (classic)`
建一个勾了 `repo` 权限的 token，把它当密码粘进去。

推送完再走第四步建 Release。传附件用网页最省事，别用命令行。
