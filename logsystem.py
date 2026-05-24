import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


def load_logs(file_path: Path) -> list[dict]:
    if not file_path.exists():
        return []
    with file_path.open("r", encoding="utf-8") as file:
        try:
            data = json.load(file)
        except json.JSONDecodeError:
            return []
        if isinstance(data, list):
            return data
    return []


def save_logs(file_path: Path, logs: list[dict]) -> None:
    file_path.parent.mkdir(parents=True, exist_ok=True)
    with file_path.open("w", encoding="utf-8") as file:
        json.dump(logs, file, ensure_ascii=False, indent=2)


def next_id(logs: list[dict]) -> int:
    valid_ids = [log.get("id") for log in logs if isinstance(log.get("id"), int)]
    return max(valid_ids, default=0) + 1


def parse_tags(tags: str | None) -> list[str]:
    if not tags:
        return []
    return [tag.strip() for tag in tags.split(",") if tag.strip()]


def add_log(file_path: Path, title: str, content: str, tags: str | None) -> None:
    logs = load_logs(file_path)
    log = {
        "id": next_id(logs),
        "title": title,
        "content": content,
        "tags": parse_tags(tags),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    logs.append(log)
    save_logs(file_path, logs)
    print(f"已新增日志 #{log['id']}")


def list_logs(file_path: Path, keyword: str | None, tag: str | None) -> None:
    logs = load_logs(file_path)
    filtered = []
    for log in logs:
        title = str(log.get("title", ""))
        content = str(log.get("content", ""))
        matches_keyword = True
        matches_tag = True
        if keyword:
            combined = f"{title} {content}".lower()
            matches_keyword = keyword.lower() in combined
        if tag:
            matches_tag = tag in log.get("tags", [])
        if matches_keyword and matches_tag:
            filtered.append(log)

    if not filtered:
        print("暂无日志")
        return

    for log in filtered:
        log_id = log.get("id", "-")
        title = str(log.get("title", ""))
        content = str(log.get("content", ""))
        created_at = str(log.get("created_at", "-"))
        tags_text = ",".join(log.get("tags", [])) or "-"
        print(f"[{log_id}] {title} | 标签: {tags_text} | 时间: {created_at}")
        print(f"    {content}")


def delete_log(file_path: Path, log_id: int) -> None:
    logs = load_logs(file_path)
    remaining = [log for log in logs if log.get("id") != log_id]
    if len(remaining) == len(logs):
        print(f"未找到日志 #{log_id}")
        return
    save_logs(file_path, remaining)
    print(f"已删除日志 #{log_id}")


def parse_args() -> argparse.Namespace:
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
    delete_parser.add_argument("--log-id", "--id", dest="log_id", required=True, type=int, help="日志 ID")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    file_path = Path(args.file)

    if args.command == "add":
        add_log(file_path, args.title, args.content, args.tags)
    elif args.command == "list":
        list_logs(file_path, args.keyword, args.tag)
    elif args.command == "delete":
        delete_log(file_path, args.log_id)


if __name__ == "__main__":
    main()
