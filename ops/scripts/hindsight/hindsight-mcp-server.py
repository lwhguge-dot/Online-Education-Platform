# /// script
# requires-python = ">=3.10"
# dependencies = ["mcp"]
# ///
import json
import logging
import re
import threading
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone, timedelta
from mcp.server.fastmcp import FastMCP

HINDSIGHT_URL = "http://127.0.0.1:8888"
TIMEOUT = 180
CST = timezone(timedelta(hours=8))

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
log = logging.getLogger("hindsight-mcp")

mcp = FastMCP("Hindsight Memory")
_pool = ThreadPoolExecutor(max_workers=8, thread_name_prefix="hindsight")


def _today() -> str:
    return datetime.now(CST).strftime("%Y-%m-%d")


def calculate_memory_quality(memory: dict) -> float:
    """Calculate quality score for a memory item (0.0 to 1.0)."""
    score = 0.0
    content = memory.get('content', '')
    metadata = memory.get('metadata', {})
    tags = memory.get('tags', [])
    
    # Content length scoring
    if len(content) > 200:
        score += 0.3
    elif len(content) > 100:
        score += 0.2
    elif len(content) > 50:
        score += 0.1
    
    # Metadata completeness scoring
    if metadata.get('category'):
        score += 0.2
    if metadata.get('date'):
        score += 0.1
    
    # Tags scoring
    if tags and len(tags) > 0:
        score += 0.1
    
    # Content quality indicators
    quality_patterns = [
        r'文件:',
        r'变更:',
        r'原因:',
        r'实现',
        r'修改',
        r'优化',
    ]
    for pattern in quality_patterns:
        if re.search(pattern, content):
            score += 0.1
            break
    
    # Time freshness scoring
    if metadata.get('date'):
        try:
            mem_date = datetime.strptime(metadata['date'], '%Y-%m-%d')
            days_old = (datetime.now() - mem_date).days
            if days_old < 1:
                score += 0.2
            elif days_old < 7:
                score += 0.1
            elif days_old < 30:
                score += 0.05
        except:
            pass
    
    return min(score, 1.0)


def calculate_overall_quality(bank_id: str) -> float:
    """Calculate overall quality score for the memory bank."""
    try:
        stats = _get(f"/v1/default/banks/{bank_id}/stats")
        total_nodes = stats.get('total_nodes', 0)
        
        if total_nodes == 0:
            return 0.0
        
        # Base score from node count
        base_score = min(total_nodes / 100, 0.5)
        
        # Score from link density
        total_links = stats.get('total_links', 0)
        link_density = total_links / max(total_nodes, 1)
        link_score = min(link_density / 10, 0.3)
        
        # Score from pending operations
        pending = stats.get('pending_operations', 0)
        pending_penalty = min(pending / 50, 0.2)
        
        return min(base_score + link_score - pending_penalty, 1.0)
    except:
        return 0.5


def cleanup_low_quality_memories(bank_id: str, threshold: float = 0.3) -> dict:
    """Delete low quality memories from the bank."""
    try:
        # Get all memories
        resp = _post(
            f"/v1/default/banks/{bank_id}/memories/recall",
            {
                "query": "all memories",
                "budget": "high",
                "max_tokens": 10000,
                "types": ["world", "experience", "observation"],
            }
        )
        
        results = resp.get('results', [])
        deleted_count = 0
        kept_count = 0
        
        for memory in results:
            quality = calculate_memory_quality(memory)
            if quality < threshold:
                # Delete low quality memory
                try:
                    _delete(f"/v1/default/banks/{bank_id}/memories/{memory['id']}")
                    deleted_count += 1
                except:
                    pass
            else:
                kept_count += 1
        
        return {
            "status": "completed",
            "deleted": deleted_count,
            "kept": kept_count,
            "threshold": threshold,
            "message": f"Cleaned up {deleted_count} low quality memories"
        }
    except Exception as e:
        return {
            "status": "error",
            "error": str(e),
            "message": "Failed to cleanup memories"
        }


def _delete(path: str) -> dict:
    """Send DELETE request to Hindsight API."""
    req = urllib.request.Request(
        f"{HINDSIGHT_URL}{path}",
        method="DELETE",
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read())


class MemoryVersionControl:
    """Memory version control system."""
    
    def __init__(self, bank_id: str):
        self.bank_id = bank_id
        self.versions = {}  # {memory_id: [{content, timestamp, version}]}
    
    def save_version(self, memory_id: str, content: str) -> dict:
        """Save a version of a memory."""
        if memory_id not in self.versions:
            self.versions[memory_id] = []
        
        version_data = {
            'content': content,
            'timestamp': datetime.now(CST).isoformat(),
            'version': len(self.versions[memory_id]) + 1
        }
        self.versions[memory_id].append(version_data)
        
        return {
            'memory_id': memory_id,
            'version': version_data['version'],
            'timestamp': version_data['timestamp']
        }
    
    def get_versions(self, memory_id: str) -> list:
        """Get all versions of a memory."""
        return self.versions.get(memory_id, [])
    
    def rollback(self, memory_id: str, version: int) -> dict:
        """Rollback to a specific version."""
        if memory_id not in self.versions:
            return {'error': 'Memory not found'}
        
        versions = self.versions[memory_id]
        if version < 1 or version > len(versions):
            return {'error': 'Invalid version'}
        
        target_version = versions[version - 1]
        
        # Update the memory content
        try:
            _patch(
                f"/v1/default/banks/{self.bank_id}/memories/{memory_id}",
                {'content': target_version['content']}
            )
            return {
                'memory_id': memory_id,
                'rollback_to': version,
                'content': target_version['content']
            }
        except Exception as e:
            return {'error': str(e)}
    
    def list_all_versions(self) -> dict:
        """List all memories with versions."""
        result = {}
        for memory_id, versions in self.versions.items():
            result[memory_id] = {
                'count': len(versions),
                'latest': versions[-1] if versions else None
            }
        return result


def extract_key_pattern(content: str) -> str:
    """Extract key pattern from memory content for deduplication."""
    # Remove common prefixes/suffixes
    content = re.sub(r'^(文件:|变更:|原因:|\[.*?\])\s*', '', content)
    content = re.sub(r'\s*(文件:|变更:|原因:|\[.*?\])$', '', content)
    
    # Normalize whitespace
    content = re.sub(r'\s+', ' ', content).strip()
    
    # Extract key identifiers
    patterns = [
        r'\b(\w+\.\w+)\b',  # filenames like foo.ts
        r'\b(\w+Function|\w+Method|\w+Class)\b',  # code identifiers
        r'(修改|添加|删除|更新|优化|重构)\s*(了)?\s*(.{0,30})',  # action patterns
    ]
    
    key_parts = []
    for pattern in patterns:
        matches = re.findall(pattern, content)
        if matches:
            key_parts.extend([str(m) for m in matches[:2]])
    
    if key_parts:
        return '|'.join(sorted(key_parts)[:3])
    
    # Fallback: use first 50 chars
    return content[:50]


def compress_memories(bank_id: str) -> dict:
    """Compress similar memories, keeping the most important ones."""
    try:
        # Get all memories
        resp = _post(
            f"/v1/default/banks/{bank_id}/memories/recall",
            {
                "query": "all memories",
                "budget": "high",
                "max_tokens": 10000,
                "types": ["world", "experience", "observation"],
            }
        )
        
        results = resp.get('results', [])
        
        # Group by key pattern
        pattern_groups = {}
        for memory in results:
            pattern = extract_key_pattern(memory.get('content', ''))
            if pattern not in pattern_groups:
                pattern_groups[pattern] = []
            pattern_groups[pattern].append(memory)
        
        # Compress each group
        compressed_count = 0
        kept_count = 0
        
        for pattern, group in pattern_groups.items():
            if len(group) > 1:
                # Keep the one with highest quality
                best = max(group, key=lambda m: calculate_memory_quality(m))
                
                # Delete others
                for memory in group:
                    if memory['id'] != best['id']:
                        try:
                            _delete(f"/v1/default/banks/{bank_id}/memories/{memory['id']}")
                            compressed_count += 1
                        except:
                            pass
                    else:
                        kept_count += 1
            else:
                kept_count += 1
        
        return {
            "status": "completed",
            "compressed": compressed_count,
            "kept": kept_count,
            "groups": len(pattern_groups),
            "message": f"Compressed {compressed_count} duplicate memories into {len(pattern_groups)} groups"
        }
    except Exception as e:
        return {
            "status": "error",
            "error": str(e),
            "message": "Failed to compress memories"
        }


def _detect_category(content: str, hint: str = "") -> str:
    """Auto-detect category from content if not explicitly set."""
    if hint in ("conversation", "code-change", "correction", "preference", "snapshot"):
        return hint
    patterns = {
        "code-change": [
            r"文件:", r"\.(java|ts|vue|py|js|tsx|jsx|css|scss|md|yml|yaml|xml|json|kt|go|rs|sql|properties|env)",
            r"pom\.xml", r"package\.json", r"Dockerfile", r"docker-compose",
            r"(?<!计划)修改(?!意见|建议)", r"新建了", r"删除了", r"变更:", r"改动:",
            r"实现(?!.*功能)", r"添加了", r"更新了", r"优化了", r"重构了",
            r"src/", r"backend/", r"frontend/", r"components/", r"services/",
            r"function", r"class", r"interface", r"type", r"export",
            r"import", r"return", r"const", r"let", r"var",
            r"\{.*\}", r"\(.*\)", r"\[.*\]",
            r"bug", r"fix", r"feature", r"refactor",
        ],
        "correction": [
            r"纠正", r"(?<!没)不对[，,。]", r"(?<!没有)不对[，,。]", r"(?<!不是)错误[的，,。]", r"说错了",
            r"错了", r"应该", r"不应该", r"需要修改", r"需要调整",
            r"建议修改", r"建议调整", r"建议优化",
        ],
        "preference": [
            r"偏好", r"习惯用", r"希望(.{0,10})这样",
            r"喜欢", r"倾向于", r"更喜欢", r"偏好",
            r"风格", r"方式", r"模式",
        ],
    }
    for cat, keywords in patterns.items():
        if any(re.search(k, content, re.IGNORECASE) for k in keywords):
            return cat
    return "conversation"


def _get(path: str) -> dict:
    req = urllib.request.Request(f"{HINDSIGHT_URL}{path}")
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read())


def _post(path: str, data: dict) -> dict:
    req = urllib.request.Request(
        f"{HINDSIGHT_URL}{path}",
        data=json.dumps(data, ensure_ascii=False).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read())


def _patch(path: str, data: dict) -> dict:
    req = urllib.request.Request(
        f"{HINDSIGHT_URL}{path}",
        data=json.dumps(data, ensure_ascii=False).encode(),
        headers={"Content-Type": "application/json"},
        method="PATCH",
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read())


def _post_background_with_retry(path: str, data: dict, max_retries: int = 3) -> None:
    """Fire-and-forget POST — 在后台线程执行，不阻塞 MCP 调用，带重试机制。"""
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(
                f"{HINDSIGHT_URL}{path}",
                data=json.dumps(data, ensure_ascii=False).encode(),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            urllib.request.urlopen(req, timeout=600)
            return
        except Exception as e:
            if attempt == max_retries - 1:
                log.error("Hindsight API request failed after %d attempts: path=%s, error=%s", max_retries, path, e)
            else:
                log.warning("Hindsight API request failed (attempt %d/%d): path=%s, error=%s", attempt + 1, max_retries, path, e)
                import time
                time.sleep(1 * (attempt + 1))


def _post_background(path: str, data: dict) -> None:
    """Fire-and-forget POST — 在后台线程执行，不阻塞 MCP 调用。"""
    _post_background_with_retry(path, data)


@mcp.tool()
def hindsight_retain(
    bank_id: str,
    content: str,
    context: str = "deepseek-code-assistant",
    category: str = "",
    date: str = "",
) -> dict:
    """保存一条记忆到 Hindsight（对话内容由 Guard 自动保存，此工具仅用于代码修改/偏好/纠正等特殊记录）。
    支持自动检测分类和自动补全日期。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    - content: 要保存的记忆内容
    - context: 来源标注，默认 deepseek-code-assistant
    - category: 分类标签(code-change/correction/preference/snapshot)，留空自动检测
    - date: 日期(YYYY-MM-DD)，留空自动使用今天
    """
    cat = _detect_category(content, category)
    dt = date or _today()
    metadata = {"category": cat, "date": dt}
    body = {
        "items": [{"content": content, "context": context, "metadata": metadata, "tags": [cat] if cat else []}]
    }
    _pool.submit(_post_background, f"/v1/default/banks/{bank_id}/memories", body)
    return {
        "status": "accepted",
        "category": cat,
        "date": dt,
        "message": "memory queued for saving",
    }


@mcp.tool()
def hindsight_retain_batch(
    bank_id: str,
    items: list[dict],
    context: str = "deepseek-code-assistant",
    date: str = "",
) -> dict:
    """批量保存多条记忆到 Hindsight（对话内容由 Guard 自动保存，此工具仅用于批量保存 code-change/correction/preference 等记录）。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    - items: [{content, category?}], category留空自动检测
    - context: 来源标注
    - date: 日期，留空自动使用今天
    """
    if not isinstance(bank_id, str) or not bank_id.strip():
        return {"status": "error", "error": "bank_id must be a non-empty string"}
    if not isinstance(items, list) or len(items) == 0:
        return {"status": "error", "error": "items must be a non-empty list"}
    for i, item in enumerate(items):
        if not isinstance(item, dict) or "content" not in item or not isinstance(item["content"], str) or not item["content"].strip():
            return {"status": "error", "error": f"items[{i}] must be a dict with a non-empty 'content' string"}
    dt = date or _today()
    payload_items = []
    for item in items:
        cat = _detect_category(item.get("content", ""), item.get("category", ""))
        payload_items.append({
            "content": item["content"],
            "context": context,
            "metadata": {"category": cat, "date": dt},
            "tags": [cat] if cat else [],
        })
    body = {"items": payload_items}
    _pool.submit(_post_background, f"/v1/default/banks/{bank_id}/memories", body)
    return {
        "status": "accepted",
        "count": len(payload_items),
        "categories": [i["metadata"]["category"] for i in payload_items],
        "date": dt,
        "message": f"{len(payload_items)} memories queued for saving",
    }


@mcp.tool()
def hindsight_recall(
    bank_id: str,
    query: str,
    budget: str = "high",
    max_tokens: int = 4096,
) -> dict:
    """从 Hindsight 检索相关记忆（通常由 Guard 插件自动执行，仅在需要精确检索特定历史时手动调用）。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    - query: 检索查询，从用户消息中提取关键词
    - budget: 检索深度，low=快速/mid=平衡/high=最详细（默认high）
    - max_tokens: 返回结果的最大Token数（默认4096）
    """
    return _post(
        f"/v1/default/banks/{bank_id}/memories/recall",
        {
            "query": query,
            "budget": budget,
            "max_tokens": max_tokens,
            "types": ["world", "experience"],
        },
    )


@mcp.tool()
def hindsight_reflect(
    bank_id: str, query: str = "", budget: str = "high"
) -> dict:
    """对已有记忆进行反思总结，生成结构化见解。用于会话快照或定期总结。
    此操作在后台异步执行，立即返回 accepted。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    - query: 总结主题，默认自动生成
    - budget: 推理深度，默认 high
    """
    q = query or "总结当前会话的完整状态：项目进度、已完成任务、待办事项、用户偏好变化"
    _pool.submit(_post_background, f"/v1/default/banks/{bank_id}/reflect", {"query": q, "budget": budget})
    return {"status": "accepted", "message": "reflect started in background"}


@mcp.tool()
def hindsight_session_end(bank_id: str) -> dict:
    """会话结束一站式工具。将 reflect 和 snapshot 保存提交到后台，立即返回。
    LLM 推理在后台进行，不阻塞 MCP 调用。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    """
    dt = _today()
    # reflect 在后台异步执行
    _pool.submit(
        _post_background,
        f"/v1/default/banks/{bank_id}/reflect",
        {"query": "总结本次会话的所有内容：完成了什么、学到了什么、用户偏好、下次需要注意的事项", "budget": "high"},
    )
    # snapshot 也在后台异步保存
    body = {
        "items": [
            {
                "content": (
                    f"## 会话结束快照 — {dt}\n\n"
                    f"本次会话已完成。后台 reflect 将生成详细的结构化摘要并补充到此节点。\n"
                    f"请稍后使用 hindsight_recall 检索最新结果。\n"
                ),
                "context": "deepseek-code-assistant",
            "metadata": {"category": "snapshot", "date": dt},
            "tags": ["snapshot"],
        }
        ]
    }
    _pool.submit(_post_background, f"/v1/default/banks/{bank_id}/memories", body)
    return {
        "status": "completed",
        "date": dt,
        "message": "session end: reflect + snapshot queued for background processing",
    }


@mcp.tool()
def hindsight_healthcheck(bank_id: str = "deepseek-v2") -> dict:
    """检查 Hindsight API 的连通性和记忆库状态。
    在 Guard 插件初始化或 AI 怀疑记忆系统不工作时调用。

    参数:
    - bank_id: 记忆库ID，默认 deepseek-v2
    """
    try:
        stats = _get(f"/v1/default/banks/{bank_id}/stats")
        quality_score = calculate_overall_quality(bank_id)
        return {
            "status": "ok",
            "api": HINDSIGHT_URL,
            "bank_id": bank_id,
            "stats": stats,
            "quality_score": round(quality_score, 2),
            "message": "Hindsight API is reachable and bank is accessible",
        }
    except Exception as e:
        return {
            "status": "error",
            "api": HINDSIGHT_URL,
            "bank_id": bank_id,
            "error": str(e),
            "message": f"Hindsight API is unreachable — check server at {HINDSIGHT_URL}",
        }


@mcp.tool()
def hindsight_bank_stats(bank_id: str) -> dict:
    """查看记忆库统计信息：记忆数量、节点类型、链接、待处理操作等。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    """
    stats = _get(f"/v1/default/banks/{bank_id}/stats")
    quality_score = calculate_overall_quality(bank_id)
    stats['quality_score'] = round(quality_score, 2)
    return stats


@mcp.tool()
def hindsight_cleanup(
    bank_id: str,
    threshold: float = 0.3,
) -> dict:
    """清理低质量记忆，释放存储空间。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    - threshold: 质量阈值，低于此值的记忆将被删除 (0.0-1.0，默认0.3)
    """
    return cleanup_low_quality_memories(bank_id, threshold)


@mcp.tool()
def hindsight_compress(
    bank_id: str,
) -> dict:
    """压缩相似记忆，保留质量最高的版本。

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    """
    return compress_memories(bank_id)


@mcp.tool()
def hindsight_update_config(
    bank_id: str,
    skepticism: int = 0,
    literalism: int = 0,
    empathy: int = 0,
    recall_budget_fixed_mid: int = 0,
    recall_budget_fixed_high: int = 0,
    consolidation_llm_batch_size: int = 0,
    retain_extraction_mode: str = "",
) -> dict:
    """动态更新 Hindsight 记忆库配置。参数设为 0 或空字符串表示不修改。

    用于调优记忆质量：
    - skepticism(1-10): 质疑程度，越高越倾向保留现有记忆而非覆盖
    - literalism(1-10): 字面理解程度，高=严格按字面，低=更自由解释
    - empathy(1-10): 共情理解程度，影响对上下文的理解
    - recall_budget_fixed_mid/high: 召回预算(token数)
    - consolidation_llm_batch_size: 合并处理的批大小
    - retain_extraction_mode: "fast"(快速) / "detailed"(详细)

    参数:
    - bank_id: 记忆库ID，固定为 deepseek-v2
    """
    overrides = {}
    if skepticism:
        overrides["disposition_skepticism"] = skepticism
    if literalism:
        overrides["disposition_literalism"] = literalism
    if empathy:
        overrides["disposition_empathy"] = empathy
    if recall_budget_fixed_mid:
        overrides["recall_budget_fixed_mid"] = recall_budget_fixed_mid
    if recall_budget_fixed_high:
        overrides["recall_budget_fixed_high"] = recall_budget_fixed_high
    if consolidation_llm_batch_size:
        overrides["consolidation_llm_batch_size"] = consolidation_llm_batch_size
    if retain_extraction_mode:
        overrides["retain_extraction_mode"] = retain_extraction_mode

    body = {"updates": overrides}
    return _patch(f"/v1/default/banks/{bank_id}/config", body)


if __name__ == "__main__":
    mcp.run(transport="stdio")
