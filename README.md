# LogSystem

一个个人使用的日志管理系统（本地命令行版）。

## 功能

- 新增日志（标题、内容、标签）
- 查看日志列表
- 通过关键字或标签过滤日志
- 删除日志

## 使用方式

在仓库目录执行：

```bash
python logsystem.py add --title "今天总结" --content "完成了日志系统原型" --tags work,python
python logsystem.py list
python logsystem.py list --keyword 原型
python logsystem.py list --tag work
python logsystem.py delete --log_id 1
```

默认数据文件：`logs.json`（位于当前目录，可通过 `--file` 指定其他路径）。
时间使用 UTC ISO 8601 格式保存。
