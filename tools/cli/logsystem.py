"""个人日志管理系统 - 本地命令行工具。

提供日志的新增、查看、过滤和删除功能，数据以 JSON 文件存储。
要求 Python 3.10+。
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def load_logs(file_path: Path) -> list[dict]:
    """从 JSON 文件加载日志列表，文件不存在或解析失败时返回空列表。"""
    if not file_path.exists():
        return []
    try:
        with file_path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except (json.JSONDecodeError, PermissionError, OSError) as exc:
        print(f"警告: 读取 {file_path} 失败 ({exc})", file=sys.stderr)
        return []
    if isinstance(data, list):
        return data
    return []


def save_logs(file_path: Path, logs: list[dict]) -> None:
    """将日志列表写入 JSON 文件，自动创建父目录。"""
    file_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with file_path.open("w", encoding="utf-8") as file:
            json.dump(logs, file, ensure_ascii=False, indent=2)
    except (PermissionError, OSError) as exc:
        print(f"错误: 写入 {file_path} 失败 ({exc})", file=sys.stderr)
        sys.exit(1)


def next_id(logs: list[dict]) -> int:
    """计算下一个可用的日志 ID（当前最大整数 ID + 1）。"""
    valid_ids = []
    for log in logs:
        current_id = log.get("id")
        if isinstance(current_id, int):
            valid_ids.append(current_id)
        elif current_id is not None:
            print(f"警告: 发现非整数 id={current_id!r}，已跳过", file=sys.stderr)
    return max(valid_ids, default=0) + 1


def parse_tags(tags: str | None) -> list[str]:
    """将逗号分隔的标签字符串解析为列表，去除空白项。"""
    if not tags:
        return []
    return [tag.strip() for tag in tags.split(",") if tag.strip()]


def validate_input(title: str, content: str) -> None:
    """校验新增日志的输入，不合法时退出。"""
    if not title.strip():
        print("错误: 标题不能为空", file=sys.stderr)
        sys.exit(1)
    if not content.strip():
        print("错误: 内容不能为空", file=sys.stderr)
        sys.exit(1)


def resolve_file_path(raw_path: str) -> Path:
    """将用户传入的文件路径规范化，防止路径遍历。"""
    path = Path(raw_path).resolve()
    return path


def add_log(file_path: Path, title: str, content: str, tags: str | None) -> None:
    """新增一条日志并保存到文件。"""
    validate_input(title, content)
    logs = load_logs(file_path)
    log = {
        "id": next_id(logs),
        "title": title.strip(),
        "content": content.strip(),
        "tags": parse_tags(tags),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    logs.append(log)
    save_logs(file_path, logs)
    print(f"已新增日志 #{log['id']}")


def filter_logs(
    logs: list[dict], keyword: str | None, tag: str | None
) -> list[dict]:
    """按关键字和标签过滤日志列表。"""
    keyword_lower = keyword.lower() if keyword else None
    tag_lower = tag.lower() if tag else None
    filtered = []
    for log in logs:
        title = str(log.get("title", ""))
        content = str(log.get("content", ""))
        matches_keyword = True
        matches_tag = True
        if keyword_lower:
            combined = f"{title} {content}".lower()
            matches_keyword = keyword_lower in combined
        if tag_lower:
            matches_tag = any(
                tag_lower == str(item).lower() for item in log.get("tags", [])
            )
        if matches_keyword and matches_tag:
            filtered.append(log)
    return filtered


def display_logs(logs: list[dict]) -> None:
    """格式化输出日志列表。"""
    if not logs:
        print("暂无日志")
        return
    for log in logs:
        log_id = log.get("id", "-")
        title = str(log.get("title", ""))
        content = str(log.get("content", ""))
        created_at = str(log.get("created_at", "-"))
        raw_tags = log.get("tags", [])
        tag_parts = []
        for tag_item in raw_tags:
            if isinstance(tag_item, str):
                tag_parts.append(tag_item)
            else:
                print(f"警告: 标签含非字符串值 {tag_item!r}，已跳过", file=sys.stderr)
        tags_text = ",".join(tag_parts) or "-"
        print(f"[{log_id}] {title} | 标签: {tags_text} | 时间: {created_at}")
        print(f"    {content}")


def delete_log(file_path: Path, log_id: int) -> None:
    """删除指定 ID 的日志。"""
    logs = load_logs(file_path)
    remaining = [log for log in logs if log.get("id") != log_id]
    if len(remaining) == len(logs):
        print(f"未找到日志 #{log_id}")
        return
    save_logs(file_path, remaining)
    print(f"已删除日志 #{log_id}")


def parse_args() -> argparse.Namespace:
    """解析命令行参数。"""
    parser = argparse.ArgumentParser(description="个人日志管理系统")
    parser.add_argument("--file", default="logs.json", help="日志数据文件路径，默认 logs.json")
    subparsers = parser.add_subparsers(dest="command", required=True)

    add_parser = subparsers.add_parser("add", help="新增日志")
    add_parser.add_argument("--title", required=True, help="日志标题")
    add_parser.add_argument("--content", required=True, help="日志内容")
    add_parser.add_argument("--tags", help="标签，逗号分隔")

    list_parser = subparsers.add_parser("list", help="查看日志列表")
    list_parser.add_argument("--keyword", help="关键字过滤（标题/内容）")
    list_parser.add_argument("--tag", help="按标签过滤")

    delete_parser = subparsers.add_parser("delete", help="删除日志")
    delete_parser.add_argument("--log-id", "-i", required=True, type=int, help="日志 ID", dest="log_id")
    return parser.parse_args()


def main() -> None:
    """程序入口。"""
    args = parse_args()
    file_path = resolve_file_path(args.file)

    if args.command == "add":
        add_log(file_path, args.title, args.content, args.tags)
    elif args.command == "list":
        logs = load_logs(file_path)
        filtered = filter_logs(logs, args.keyword, args.tag)
        display_logs(filtered)
    elif args.command == "delete":
        delete_log(file_path, args.log_id)


if __name__ == "__main__":
    main()
