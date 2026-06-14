# /// script
# requires-python = ">=3.10"
# ///
"""Hindsight 会话记忆独立保存工具。

不依赖 MCP 协议，直接通过 HTTP API 调用 Hindsight 服务。
可在 AI 会话外部独立运行，作为会话关闭守卫。
"""

import argparse
import json
import sys
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta
import time
import logging
from pathlib import Path

# 配置日志
LOG_DIR = Path(__file__).parent.parent.parent / "logs"
LOG_DIR.mkdir(exist_ok=True)
LOG_FILE = LOG_DIR / "session-guard.log"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(LOG_FILE, encoding="utf-8"),
        logging.StreamHandler()
    ]
)
log = logging.getLogger("session-guard")

HINDSIGHT_URL = "http://localhost:8888"
TIMEOUT = 180
CST = timezone(timedelta(hours=8))
MAX_RETRIES = 3
RETRY_DELAY = 2


def _post_with_retry(path: str, data: dict, max_retries: int = MAX_RETRIES) -> dict:
    """发送 POST 请求，带重试机制。"""
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(
                f"{HINDSIGHT_URL}{path}",
                data=json.dumps(data, ensure_ascii=False).encode(),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                return json.loads(resp.read())
        except Exception as e:
            if attempt == max_retries - 1:
                log.error("POST 请求失败 (已重试 %d 次): path=%s, error=%s", max_retries, path, e)
                raise
            else:
                log.warning("POST 请求失败 (尝试 %d/%d): path=%s, error=%s", attempt + 1, max_retries, path, e)
                time.sleep(RETRY_DELAY * (attempt + 1))
    return {}


def _post(path: str, data: dict) -> dict:
    """发送 POST 请求。"""
    return _post_with_retry(path, data)


def check_health() -> bool:
    """检查 Hindsight 服务健康状态。"""
    try:
        req = urllib.request.Request(f"{HINDSIGHT_URL}/health")
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = json.loads(resp.read())
            if result.get("status") == "healthy":
                log.info("Hindsight 服务健康")
                return True
            log.warning("Hindsight 状态异常: %s", result)
            return False
    except Exception as e:
        log.error("无法连接 Hindsight: %s", e)
        return False


def save_summary(bank_id: str, summary: str, date: str):
    """保存会话摘要。"""
    content = f"会话记忆保存 | {summary}"
    try:
        result = _post(
            f"/v1/default/banks/{bank_id}/memories",
            {
                "items": [
                    {
                        "content": content,
                        "context": "session-guard",
                        "metadata": {"category": "snapshot", "date": date},
                    }
                ]
            },
        )
        log.info("会话摘要已保存: %s 条", result.get('saved', result.get('count', '?')))
    except Exception as e:
        log.error("保存会话摘要失败: %s", e)
        raise


def save_final_snapshot(bank_id: str, date: str):
    """保存最终会话快照。"""
    try:
        reflect_result = _post(
            f"/v1/default/banks/{bank_id}/reflect",
            {
                "query": "总结当前会话的所有内容：完成了什么、学到了什么、用户偏好、下次需要注意的事项",
                "budget": "high",
            },
        )
        log.info("Reflect 完成: %s", reflect_result.get('status', 'ok'))
    except Exception as e:
        log.error("Reflect 失败: %s", e)
        # 继续保存快照，不中断

    summary = "会话结束守护自动保存"
    try:
        result = _post(
            f"/v1/default/banks/{bank_id}/memories",
            {
                "items": [
                    {
                        "content": f"## 会话关闭快照\n\n日期: {date}\n\n{summary}",
                        "context": "session-guard",
                        "metadata": {"category": "snapshot", "date": date},
                    }
                ]
            },
        )
        log.info("最终快照已保存: %s 条", result.get('saved', result.get('count', '?')))
    except Exception as e:
        log.error("保存最终快照失败: %s", e)
        raise


def main():
    parser = argparse.ArgumentParser(description="Hindsight 会话记忆保存工具")
    parser.add_argument("--bank-id", default="deepseek-v2", help="记忆库 ID")
    parser.add_argument("--date", default="", help="日期 (YYYY-MM-DD)，默认今天")
    parser.add_argument("--summary", default="", help="会话摘要内容")
    parser.add_argument("--final", action="store_true", help="会话结束最终快照")
    args = parser.parse_args()

    date = args.date or datetime.now(CST).strftime("%Y-%m-%d")
    log.info("开始执行会话记忆保存，日期: %s", date)

    if not check_health():
        log.error("Hindsight 服务不健康，退出")
        sys.exit(1)

    try:
        if args.final:
            save_final_snapshot(args.bank_id, date)
        elif args.summary:
            save_summary(args.bank_id, args.summary, date)
        else:
            log.error("用法: --summary '...' 保存摘要 | --final 保存最终快照")
            sys.exit(1)

        log.info("会话记忆保存完成")
    except Exception as e:
        log.error("会话记忆保存失败: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
