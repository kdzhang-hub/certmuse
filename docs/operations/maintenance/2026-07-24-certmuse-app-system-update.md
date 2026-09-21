# 77 系统升级与 APT 清理记录

- 日期：2026-07-24（Asia/Taipei）
- 目标：`certmuse-app`（192.168.6.77）
- 执行路径：项目本地 `tools/ops/local/srv-ops/bin/srvctl`

## 执行内容

1. 刷新 APT 索引并安装全部 5 项待升级的安全包：`bind9-dnsutils`、`bind9-host`、`bind9-libs`、`linux-image-amd64`、`linux-libc-dev`。
2. 安装 Linux `6.12.96` 内核后重启主机。
3. 先模拟、后执行 `apt autoremove --purge`：移除 42 个 APT 标记为不再需要的软件包，主要为 Maven/Java 构建依赖与旧内核 `6.12.63`；预计释放 128 MB。
4. 执行 `apt clean` 清除 APT 下载缓存。

未删除应用目录、PostgreSQL/Redis 数据、原始资料库或应用日志。

## 验证结果

- 运行内核：`6.12.96+deb13-amd64`。
- `apt list --upgradable` 无待升级包。
- `systemctl --failed --no-pager`：0 个失败单元。
- Nginx、`postgresql@16-main`、Redis 均为 `active (running)`。
- 根文件系统：93G，总已用约 3.6G、可用约 85G（4%）。
