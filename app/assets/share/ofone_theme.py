#!/usr/bin/env python3
"""把主题设置写入 OpenFic 数据库（服务未启动时使用）。"""
import random
import sqlite3
import sys
from datetime import UTC, datetime

DB = "/root/.openfic/openfic.db"
KEY = "theme"
VALUES = ("system", "light", "dark")
CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"


def main() -> int:
    theme = sys.argv[1] if len(sys.argv) > 1 else ""
    if theme not in VALUES:
        print("OFONE_THEME_ERROR=bad-theme")
        return 1
    try:
        conn = sqlite3.connect(DB, timeout=8)
    except Exception as exc:
        print("OFONE_THEME_MISSING=db")
        print("OFONE_THEME_ERROR=%s" % exc)
        return 1
    try:
        table = conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='settings'"
        ).fetchone()
        if not table:
            print("OFONE_THEME_MISSING=table")
            return 1
        now = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
        row = conn.execute("SELECT id FROM settings WHERE key=?", (KEY,)).fetchone()
        if row:
            conn.execute(
                "UPDATE settings SET value=?, updated_at=? WHERE id=?",
                (theme, now, row[0]),
            )
        else:
            new_id = "".join(random.choice(CHARS) for _ in range(21))
            conn.execute(
                "INSERT INTO settings (id, key, value, created_at, updated_at) VALUES (?,?,?,?,?)",
                (new_id, KEY, theme, now, now),
            )
        conn.commit()
    except Exception as exc:
        print("OFONE_THEME_ERROR=%s" % exc)
        return 1
    finally:
        conn.close()
    print("OFONE_THEME_OK=%s" % theme)
    return 0


if __name__ == "__main__":
    sys.exit(main())
