"""tools/cli/logsystem.py 的单元测试。"""

import json
import runpy
import sys
from pathlib import Path
from unittest.mock import patch

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools" / "cli"))
from logsystem import (
    add_log,
    delete_log,
    display_logs,
    filter_logs,
    load_logs,
    main,
    next_id,
    parse_args,
    parse_tags,
    resolve_file_path,
    save_logs,
    validate_input,
)


@pytest.fixture
def tmp_log_file(tmp_path: Path) -> Path:
    return tmp_path / "logs.json"


@pytest.fixture
def sample_logs() -> list[dict]:
    return [
        {
            "id": 1,
            "title": "第一条",
            "content": "内容A",
            "tags": ["work", "python"],
            "created_at": "2026-01-01T00:00:00+00:00",
        },
        {
            "id": 2,
            "title": "第二条",
            "content": "内容B",
            "tags": ["life"],
            "created_at": "2026-01-02T00:00:00+00:00",
        },
    ]


# ── load_logs ──────────────────────────────────────────────


class TestLoadLogs:
    def test_file_not_exists_returns_empty(self, tmp_log_file: Path) -> None:
        assert load_logs(tmp_log_file) == []

    def test_valid_json_list(self, tmp_log_file: Path, sample_logs: list[dict]) -> None:
        tmp_log_file.write_text(json.dumps(sample_logs), encoding="utf-8")
        assert load_logs(tmp_log_file) == sample_logs

    def test_invalid_json_returns_empty(self, tmp_log_file: Path) -> None:
        tmp_log_file.write_text("{bad json", encoding="utf-8")
        assert load_logs(tmp_log_file) == []

    def test_non_list_json_returns_empty(self, tmp_log_file: Path) -> None:
        tmp_log_file.write_text('{"not": "a list"}', encoding="utf-8")
        assert load_logs(tmp_log_file) == []


# ── save_logs ──────────────────────────────────────────────


class TestSaveLogs:
    def test_creates_file_and_parent_dirs(self, tmp_path: Path) -> None:
        target = tmp_path / "sub" / "dir" / "logs.json"
        save_logs(target, [{"id": 1}])
        assert json.loads(target.read_text(encoding="utf-8")) == [{"id": 1}]

    def test_roundtrip(self, tmp_log_file: Path, sample_logs: list[dict]) -> None:
        save_logs(tmp_log_file, sample_logs)
        assert load_logs(tmp_log_file) == sample_logs


# ── next_id ────────────────────────────────────────────────


class TestNextId:
    def test_empty_list(self) -> None:
        assert next_id([]) == 1

    def test_returns_max_plus_one(self) -> None:
        assert next_id([{"id": 3}, {"id": 1}]) == 4

    def test_skips_non_integer_ids(self, capsys: pytest.CaptureFixture[str]) -> None:
        logs = [{"id": "abc"}, {"id": 5}]
        result = next_id(logs)
        assert result == 6
        captured = capsys.readouterr()
        assert "非整数" in captured.err

    def test_all_non_integer_returns_one(self) -> None:
        assert next_id([{"id": "abc"}, {"id": None}]) == 1


# ── parse_tags ─────────────────────────────────────────────


class TestParseTags:
    def test_none_returns_empty(self) -> None:
        assert parse_tags(None) == []

    def test_empty_string_returns_empty(self) -> None:
        assert parse_tags("") == []

    def test_splits_and_strips(self) -> None:
        assert parse_tags(" work , python ") == ["work", "python"]

    def test_filters_blank_items(self) -> None:
        assert parse_tags("a,,b,") == ["a", "b"]


# ── validate_input ─────────────────────────────────────────


class TestValidateInput:
    def test_valid_input_passes(self) -> None:
        validate_input("title", "content")  # should not raise

    def test_empty_title_exits(self) -> None:
        with pytest.raises(SystemExit):
            validate_input("", "content")

    def test_blank_content_exits(self) -> None:
        with pytest.raises(SystemExit):
            validate_input("title", "   ")


# ── resolve_file_path ──────────────────────────────────────


class TestResolveFilePath:
    def test_returns_absolute_path(self) -> None:
        result = resolve_file_path("logs.json")
        assert result.is_absolute()

    def test_resolves_dotdot(self) -> None:
        result = resolve_file_path("sub/../logs.json")
        assert ".." not in str(result)


# ── add_log ────────────────────────────────────────────────


class TestAddLog:
    def test_add_creates_entry(self, tmp_log_file: Path) -> None:
        add_log(tmp_log_file, "标题", "内容", "a,b")
        logs = load_logs(tmp_log_file)
        assert len(logs) == 1
        assert logs[0]["title"] == "标题"
        assert logs[0]["tags"] == ["a", "b"]

    def test_add_increments_id(self, tmp_log_file: Path) -> None:
        add_log(tmp_log_file, "t1", "c1", None)
        add_log(tmp_log_file, "t2", "c2", None)
        logs = load_logs(tmp_log_file)
        assert logs[0]["id"] == 1
        assert logs[1]["id"] == 2

    def test_strips_whitespace(self, tmp_log_file: Path) -> None:
        add_log(tmp_log_file, "  标题  ", "  内容  ", None)
        logs = load_logs(tmp_log_file)
        assert logs[0]["title"] == "标题"
        assert logs[0]["content"] == "内容"


# ── filter_logs ────────────────────────────────────────────


class TestFilterLogs:
    def test_no_filters_returns_all(self, sample_logs: list[dict]) -> None:
        assert filter_logs(sample_logs, None, None) == sample_logs

    def test_keyword_match(self, sample_logs: list[dict]) -> None:
        result = filter_logs(sample_logs, "第一条", None)
        assert len(result) == 1

    def test_keyword_case_insensitive(self, sample_logs: list[dict]) -> None:
        result = filter_logs(sample_logs, "内容", None)
        assert len(result) == 2

    def test_tag_match(self, sample_logs: list[dict]) -> None:
        result = filter_logs(sample_logs, None, "work")
        assert len(result) == 1

    def test_tag_case_insensitive(self, sample_logs: list[dict]) -> None:
        result = filter_logs(sample_logs, None, "WORK")
        assert len(result) == 1

    def test_combined_filters(self, sample_logs: list[dict]) -> None:
        result = filter_logs(sample_logs, "内容", "python")
        assert len(result) == 1

    def test_no_match_returns_empty(self, sample_logs: list[dict]) -> None:
        assert filter_logs(sample_logs, "不存在", None) == []


# ── display_logs ───────────────────────────────────────────


class TestDisplayLogs:
    def test_empty_list(self, capsys: pytest.CaptureFixture[str]) -> None:
        display_logs([])
        assert "暂无日志" in capsys.readouterr().out

    def test_prints_entries(self, sample_logs: list[dict], capsys: pytest.CaptureFixture[str]) -> None:
        display_logs(sample_logs)
        out = capsys.readouterr().out
        assert "[1]" in out
        assert "[2]" in out
        assert "work,python" in out

    def test_warns_on_non_string_tag(self, capsys: pytest.CaptureFixture[str]) -> None:
        logs = [{"id": 1, "title": "t", "content": "c", "tags": [123]}]
        display_logs(logs)
        captured = capsys.readouterr()
        assert "非字符串" in captured.err


# ── delete_log ─────────────────────────────────────────────


class TestDeleteLog:
    def test_deletes_existing(self, tmp_log_file: Path, sample_logs: list[dict]) -> None:
        save_logs(tmp_log_file, sample_logs)
        delete_log(tmp_log_file, 1)
        remaining = load_logs(tmp_log_file)
        assert len(remaining) == 1
        assert remaining[0]["id"] == 2

    def test_nonexistent_id_prints_message(self, tmp_log_file: Path, capsys: pytest.CaptureFixture[str]) -> None:
        save_logs(tmp_log_file, [])
        delete_log(tmp_log_file, 999)
        assert "未找到" in capsys.readouterr().out


# ── save_logs error path ───────────────────────────────────


class TestSaveLogsErrors:
    def test_permission_error_exits(self, tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
        target = tmp_path / "readonly" / "logs.json"
        with patch("logsystem.Path.open", side_effect=PermissionError("denied")):
            with pytest.raises(SystemExit):
                save_logs(target, [{"id": 1}])
        assert "失败" in capsys.readouterr().err


# ── parse_args ─────────────────────────────────────────────


class TestParseArgs:
    def test_add_command(self) -> None:
        with patch("sys.argv", ["logsystem.py", "add", "--title", "t", "--content", "c"]):
            args = parse_args()
        assert args.command == "add"
        assert args.title == "t"

    def test_list_command(self) -> None:
        with patch("sys.argv", ["logsystem.py", "list", "--keyword", "k"]):
            args = parse_args()
        assert args.command == "list"
        assert args.keyword == "k"

    def test_delete_command(self) -> None:
        with patch("sys.argv", ["logsystem.py", "delete", "--log-id", "5"]):
            args = parse_args()
        assert args.command == "delete"
        assert args.log_id == 5


# ── main (CLI integration) ─────────────────────────────────


class TestMain:
    def test_add_via_main(self, tmp_path: Path) -> None:
        log_file = tmp_path / "test.json"
        with patch("sys.argv", ["logsystem.py", "--file", str(log_file), "add", "--title", "t", "--content", "c"]):
            main()
        logs = load_logs(log_file)
        assert len(logs) == 1

    def test_list_via_main(self, tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
        log_file = tmp_path / "test.json"
        save_logs(log_file, [{"id": 1, "title": "t", "content": "c", "tags": [], "created_at": ""}])
        with patch("sys.argv", ["logsystem.py", "--file", str(log_file), "list"]):
            main()
        assert "[1]" in capsys.readouterr().out

    def test_delete_via_main(self, tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
        log_file = tmp_path / "test.json"
        save_logs(log_file, [{"id": 1, "title": "t", "content": "c", "tags": [], "created_at": ""}])
        with patch("sys.argv", ["logsystem.py", "--file", str(log_file), "delete", "--log-id", "1"]):
            main()
        assert load_logs(log_file) == []


# ── load_logs error path ───────────────────────────────────


class TestLoadLogsErrors:
    def test_permission_error_returns_empty(self, tmp_log_file: Path, capsys: pytest.CaptureFixture[str]) -> None:
        tmp_log_file.write_text("{}", encoding="utf-8")
        with patch("logsystem.Path.open", side_effect=PermissionError("denied")):
            result = load_logs(tmp_log_file)
        assert result == []
        assert "失败" in capsys.readouterr().err
