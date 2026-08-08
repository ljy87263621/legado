# Windows Portable 发布

该目录版发布不依赖系统预装 Java、Node.js 或 MSIX/AppX 注册。Compose Desktop 会把 `Legado.exe`、应用 JAR、原生库和 bundled JVM 放在同一目录中，并通过根目录的 `portable.flag` 将默认数据放在同目录的 `data` 子目录。

## 构建并验证

在仓库根目录执行：

```powershell
& .\desktop\packaging\windows\portable.ps1 -Build
```

脚本会执行 `:desktop:app:createDistributable`，检查以下内容：

- `Legado.exe`
- `portable.flag`
- `app` 目录、至少一个应用 JAR、`.jpackage.xml` 和 `Legado.cfg`
- `runtime\bin\server\jvm.dll` 与 `runtime\release`
- bundled JVM 包含 `java.net.http`，用于桌面 WebDAV/HTTP 客户端初始化
- 使用临时 `LEGADO_DATA_DIR` 启动，并等待 `legado.db` 创建
- 终止验证进程并清理脚本自己创建的临时目录

只检查已有目录：

```powershell
& .\desktop\packaging\windows\portable.ps1 -VerifyOnly
```

## 分发到指定目录

先完成构建验证，再将整个目录复制到目标目录。Portable 发布的默认数据目录是发布目录下的 `data`；`--data-dir` 参数或 `LEGADO_DATA_DIR` 环境变量可以显式指定其它目录。已有 `%LOCALAPPDATA%\Legado\legado.db` 不会被自动覆盖或迁移，升级前应保留备份。

```powershell
$source = (Resolve-Path '.\desktop\app\build\compose\binaries\main\app\Legado').Path
$target = 'D:\Apps\Legado'
New-Item -ItemType Directory -Path $target -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source '*') -Destination $target -Recurse -Force
```

复制后首次启动会自动创建 `D:\Apps\Legado\data\legado.db`。不要删除 `portable.flag`，否则程序会回退到 `%LOCALAPPDATA%\Legado`。

首次启动时程序会在当前用户下注册 `yuedu://` 和 `legado://` 协议，用于处理在线书源导入链接。该注册写入当前用户注册表，不需要管理员权限。
