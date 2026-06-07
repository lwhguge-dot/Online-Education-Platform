# /// script
# requires-python = ">=3.10"
# ///
"""Hindsight 记忆分析工具。

提供记忆使用情况分析、质量评估和优化建议。
"""

import json
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta
from collections import Counter
import re

HINDSIGHT_URL = "http://localhost:8888"
TIMEOUT = 180
CST = timezone(timedelta(hours=8))


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


def analyze_memory_usage(bank_id: str) -> dict:
    """分析记忆使用情况。"""
    try:
        # Get stats
        stats = _get(f"/v1/default/banks/{bank_id}/stats")
        
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
        
        # Analyze categories
        category_counter = Counter()
        for memory in results:
            category = memory.get('metadata', {}).get('category', 'unknown')
            category_counter[category] += 1
        
        # Analyze quality distribution
        quality_scores = []
        for memory in results:
            quality = calculate_memory_quality(memory)
            quality_scores.append(quality)
        
        avg_quality = sum(quality_scores) / len(quality_scores) if quality_scores else 0
        
        # Analyze temporal distribution
        date_counter = Counter()
        for memory in results:
            date = memory.get('metadata', {}).get('date', 'unknown')
            date_counter[date] += 1
        
        # Analyze content patterns
        pattern_counter = Counter()
        for memory in results:
            content = memory.get('content', '')
            # Extract common patterns
            if '文件:' in content:
                pattern_counter['file_reference'] += 1
            if '修改' in content:
                pattern_counter['modification'] += 1
            if '添加' in content:
                pattern_counter['addition'] += 1
            if '优化' in content:
                pattern_counter['optimization'] += 1
        
        # Calculate statistics
        total_memories = len(results)
        quality_distribution = {
            'high': sum(1 for q in quality_scores if q >= 0.7),
            'medium': sum(1 for q in quality_scores if 0.3 <= q < 0.7),
            'low': sum(1 for q in quality_scores if q < 0.3)
        }
        
        return {
            'bank_id': bank_id,
            'total_memories': total_memories,
            'total_nodes': stats.get('total_nodes', 0),
            'total_links': stats.get('total_links', 0),
            'categories': dict(category_counter),
            'quality_distribution': quality_distribution,
            'average_quality': round(avg_quality, 2),
            'temporal_distribution': dict(date_counter),
            'content_patterns': dict(pattern_counter),
            'recommendations': generate_recommendations(
                total_memories, category_counter, quality_distribution, avg_quality
            )
        }
    except Exception as e:
        return {
            'status': 'error',
            'error': str(e),
            'message': 'Failed to analyze memory usage'
        }


def generate_recommendations(
    total_memories: int,
    category_counter: Counter,
    quality_distribution: dict,
    avg_quality: float
) -> list:
    """生成优化建议。"""
    recommendations = []
    
    # Check total memories
    if total_memories > 1000:
        recommendations.append({
            'type': 'cleanup',
            'priority': 'high',
            'message': f'记忆库包含 {total_memories} 条记忆，建议执行清理'
        })
    
    # Check quality distribution
    if quality_distribution['low'] > total_memories * 0.3:
        recommendations.append({
            'type': 'quality',
            'priority': 'high',
            'message': f'低质量记忆占比 {quality_distribution["low"]/total_memories*100:.1f}%，建议清理'
        })
    
    # Check average quality
    if avg_quality < 0.5:
        recommendations.append({
            'type': 'quality',
            'priority': 'medium',
            'message': f'平均质量评分 {avg_quality:.2f}，建议优化记忆保存策略'
        })
    
    # Check category balance
    if category_counter.get('conversation', 0) > total_memories * 0.8:
        recommendations.append({
            'type': 'category',
            'priority': 'medium',
            'message': '对话记忆占比过高，建议增加 code-change 类型记忆'
        })
    
    # Check for duplicates
    if total_memories > 100:
        recommendations.append({
            'type': 'compression',
            'priority': 'low',
            'message': '记忆库较大，建议执行压缩以减少重复'
        })
    
    return recommendations


def generate_report(analysis: dict) -> str:
    """生成分析报告。"""
    report = f"""
# Hindsight 记忆分析报告

## 基本信息
- 记忆库 ID: {analysis.get('bank_id', 'N/A')}
- 总记忆数: {analysis.get('total_memories', 0)}
- 总节点数: {analysis.get('total_nodes', 0)}
- 总链接数: {analysis.get('total_links', 0)}

## 分类分布
"""
    for category, count in analysis.get('categories', {}).items():
        report += f"- {category}: {count} 条\n"
    
    report += f"""
## 质量分布
- 高质量 (≥0.7): {analysis.get('quality_distribution', {}).get('high', 0)} 条
- 中等质量 (0.3-0.7): {analysis.get('quality_distribution', {}).get('medium', 0)} 条
- 低质量 (<0.3): {analysis.get('quality_distribution', {}).get('low', 0)} 条
- 平均质量: {analysis.get('average_quality', 0)}

## 时间分布
"""
    for date, count in sorted(analysis.get('temporal_distribution', {}).items()):
        report += f"- {date}: {count} 条\n"
    
    report += f"""
## 内容模式
"""
    for pattern, count in analysis.get('content_patterns', {}).items():
        report += f"- {pattern}: {count} 次\n"
    
    report += f"""
## 优化建议
"""
    for rec in analysis.get('recommendations', []):
        report += f"- [{rec['priority'].upper()}] {rec['message']}\n"
    
    return report


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="Hindsight 记忆分析工具")
    parser.add_argument("--bank-id", default="deepseek-v2", help="记忆库 ID")
    parser.add_argument("--output", default="", help="输出文件路径")
    args = parser.parse_args()
    
    print("开始分析记忆使用情况...")
    analysis = analyze_memory_usage(args.bank_id)
    
    if 'error' in analysis:
        print(f"分析失败: {analysis['error']}")
        return
    
    # Generate report
    report = generate_report(analysis)
    
    # Output
    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"报告已保存到: {args.output}")
    else:
        print(report)
    
    # Also output JSON
    json_output = args.output.replace('.md', '.json') if args.output else 'memory-analysis.json'
    with open(json_output, 'w', encoding='utf-8') as f:
        json.dump(analysis, f, ensure_ascii=False, indent=2)
    print(f"JSON 数据已保存到: {json_output}")


if __name__ == "__main__":
    main()
