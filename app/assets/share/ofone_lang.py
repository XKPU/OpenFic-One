# -*- coding: utf-8 -*-
"""服务未运行时直接写入 OpenFic 的界面语言。"""

import os
import random
import sqlite3
import sys
from datetime import UTC, datetime

DB = "/root/.openfic/openfic.db"
CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
KEY = "language"


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else ""
    if lang not in ("zh-CN", "en"):
        print("OFONE_LANG_ERROR=bad-lang")
        return 1
    if not os.path.exists(DB):
        print("OFONE_LANG_MISSING=db")
        return 1
    now = datetime.now(UTC).strftime("%Y-%m-%d %H:%M:%S.%f")
    conn = sqlite3.connect(DB, timeout=8)
    try:
        if not conn.execute(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='settings'"
        ).fetchone():
            print("OFONE_LANG_MISSING=table")
            return 1
        row = conn.execute("SELECT id FROM settings WHERE key=?", (KEY,)).fetchone()
        if row:
            conn.execute(
                "UPDATE settings SET value=?, updated_at=? WHERE key=?", (lang, now, KEY)
            )
        else:
            new_id = "".join(random.choice(CHARS) for _ in range(21))
            conn.execute(
                "INSERT INTO settings (id, key, value, created_at, updated_at)"
                " VALUES (?,?,?,?,?)",
                (new_id, KEY, lang, now, now),
            )
        conn.commit()
    except sqlite3.Error as e:
        print("OFONE_LANG_ERROR=%s" % e)
        return 1
    finally:
        conn.close()
    print("OFONE_LANG_OK=%s" % lang)
    return 0


if __name__ == "__main__":
    sys.exit(main())
