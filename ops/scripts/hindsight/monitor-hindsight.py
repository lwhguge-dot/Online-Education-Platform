# /// script
# requires-python = ">=3.10"
# ///
"""Hindsight 监控工具。

收集和报告系统性能指标。
"""

import json
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta
import time
import psutil
import os

HINDSIGHT_URL = "http://localhost:8888"
TIMEOUT = 180
CST = timezone(timedelta(hours=8))


def _get(path: str) -> dict:
    req = urllib.request.Request(f"{HINDSIGHT_URL}{path}")
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read())


def check_health() -> dict:
    """检查 Hindsight 服务健康状态。"""
    try:
        start_time = time.time()
        resp = _get("/health")
        response_time = time.time() - start_time
        
        return {
            'status': 'healthy' if resp.get('status') == 'healthy' else 'unhealthy',
            'response_time': round(response_time, 3),
            'timestamp': datetime.now(CST).isoformat(),
            'details': resp
        }
    except Exception as e:
        return {
            'status': 'error',
            'error': str(e),
            'timestamp': datetime.now(CST).isoformat()
        }


def get_system_metrics() -> dict:
    """获取系统指标。"""
    try:
        # CPU usage
        cpu_percent = psutil.cpu_percent(interval=1)
        
        # Memory usage
        memory = psutil.virtual_memory()
        
        # Disk usage
        disk = psutil.disk_usage('/')
        
        # Network I/O
        net_io = psutil.net_io_counters()
        
        return {
            'cpu_percent': cpu_percent,
            'memory_total': memory.total,
            'memory_used': memory.used,
            'memory_percent': memory.percent,
            'disk_total': disk.total,
            'disk_used': disk.used,
            'disk_percent': disk.percent,
            'net_bytes_sent': net_io.bytes_sent,
            'net_bytes_recv': net_io.bytes_recv,
            'timestamp': datetime.now(CST).isoformat()
        }
    except Exception as e:
        return {
            'error': str(e),
            'timestamp': datetime.now(CST).isoformat()
        }


def get_memory_bank_stats(bank_id: str) -> dict:
    """获取记忆库统计信息。"""
    try:
        stats = _get(f"/v1/default/banks/{bank_id}/stats")
        return {
            'bank_id': bank_id,
            'total_nodes': stats.get('total_nodes', 0),
            'total_links': stats.get('total_links', 0),
            'pending_operations': stats.get('pending_operations', 0),
            'failed_operations': stats.get('failed_operations', 0),
            'last_consolidated_at': stats.get('last_consolidated_at'),
            'timestamp': datetime.now(CST).isoformat()
        }
    except Exception as e:
        return {
            'bank_id': bank_id,
            'error': str(e),
            'timestamp': datetime.now(CST).isoformat()
        }


def monitor_performance(duration: int = 60, interval: int = 5) -> dict:
    """监控性能指标。"""
    print(f"开始监控 {duration} 秒，采样间隔 {interval} 秒...")
    
    metrics = []
    start_time = time.time()
    
    while time.time() - start_time < duration:
        # Collect metrics
        health = check_health()
        system = get_system_metrics()
        
        metrics.append({
            'timestamp': datetime.now(CST).isoformat(),
            'health': health,
            'system': system
        })
        
        # Print current status
        status = health.get('status', 'unknown')
        cpu = system.get('cpu_percent', 0)
        memory = system.get('memory_percent', 0)
        
        print(f"[{datetime.now(CST).strftime('%H:%M:%S')}] "
              f"状态: {status} | "
              f"CPU: {cpu}% | "
              f"内存: {memory}% | "
              f"响应时间: {health.get('response_time', 0):.3f}s")
        
        time.sleep(interval)
    
    # Calculate statistics
    response_times = [m['health'].get('response_time', 0) for m in metrics]
    cpu_values = [m['system'].get('cpu_percent', 0) for m in metrics]
    memory_values = [m['system'].get('memory_percent', 0) for m in metrics]
    
    healthy_count = sum(1 for m in metrics if m['health'].get('status') == 'healthy')
    
    return {
        'duration': duration,
        'interval': interval,
        'total_samples': len(metrics),
        'healthy_samples': healthy_count,
        'availability': round(healthy_count / len(metrics) * 100, 2) if metrics else 0,
        'response_time': {
            'min': round(min(response_times), 3) if response_times else 0,
            'max': round(max(response_times), 3) if response_times else 0,
            'avg': round(sum(response_times) / len(response_times), 3) if response_times else 0,
        },
        'cpu': {
            'min': round(min(cpu_values), 2) if cpu_values else 0,
            'max': round(max(cpu_values), 2) if cpu_values else 0,
            'avg': round(sum(cpu_values) / len(cpu_values), 2) if cpu_values else 0,
        },
        'memory': {
            'min': round(min(memory_values), 2) if memory_values else 0,
            'max': round(max(memory_values), 2) if memory_values else 0,
            'avg': round(sum(memory_values) / len(memory_values), 2) if memory_values else 0,
        },
        'metrics': metrics
    }


def generate_monitoring_report(health: dict, system: dict, bank_stats: dict, performance: dict = None) -> str:
    """生成监控报告。"""
    report = f"""
# Hindsight 监控报告

## 服务健康状态
- 状态: {health.get('status', 'unknown')}
- 响应时间: {health.get('response_time', 0):.3f}s
- 时间: {health.get('timestamp', 'N/A')}

## 系统资源
- CPU 使用率: {system.get('cpu_percent', 0):.2f}%
- 内存使用率: {system.get('memory_percent', 0):.2f}%
- 磁盘使用率: {system.get('disk_percent', 0):.2f}%
- 网络发送: {system.get('net_bytes_sent', 0) / 1024 / 1024:.2f} MB
- 网络接收: {system.get('net_bytes_recv', 0) / 1024 / 1024:.2f} MB

## 记忆库状态
- 记忆库 ID: {bank_stats.get('bank_id', 'N/A')}
- 总节点数: {bank_stats.get('total_nodes', 0)}
- 总链接数: {bank_stats.get('total_links', 0)}
- 待处理操作: {bank_stats.get('pending_operations', 0)}
- 失败操作: {bank_stats.get('failed_operations', 0)}
- 最后合并时间: {bank_stats.get('last_consolidated_at', 'N/A')}
"""
    
    if performance:
        report += f"""
## 性能统计
- 监控时长: {performance.get('duration', 0)} 秒
- 总采样数: {performance.get('total_samples', 0)}
- 健康采样数: {performance.get('healthy_samples', 0)}
- 可用性: {performance.get('availability', 0)}%
- 响应时间: {performance['response_time']['min']:.3f}s ~ {performance['response_time']['max']:.3f}s (平均: {performance['response_time']['avg']:.3f}s)
- CPU 使用率: {performance['cpu']['min']:.2f}% ~ {performance['cpu']['max']:.2f}% (平均: {performance['cpu']['avg']:.2f}%)
- 内存使用率: {performance['memory']['min']:.2f}% ~ {performance['memory']['max']:.2f}% (平均: {performance['memory']['avg']:.2f}%)
"""
    
    return report


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="Hindsight 监控工具")
    parser.add_argument("--bank-id", default="deepseek-v2", help="记忆库 ID")
    parser.add_argument("--monitor", action="store_true", help="启动性能监控")
    parser.add_argument("--duration", type=int, default=60, help="监控时长（秒）")
    parser.add_argument("--interval", type=int, default=5, help="采样间隔（秒）")
    parser.add_argument("--output", default="", help="输出文件路径")
    args = parser.parse_args()
    
    print("开始收集监控数据...")
    
    # Collect data
    health = check_health()
    system = get_system_metrics()
    bank_stats = get_memory_bank_stats(args.bank_id)
    
    performance = None
    if args.monitor:
        performance = monitor_performance(args.duration, args.interval)
    
    # Generate report
    report = generate_monitoring_report(health, system, bank_stats, performance)
    
    # Output
    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"报告已保存到: {args.output}")
    else:
        print(report)
    
    # Also output JSON
    json_output = args.output.replace('.md', '.json') if args.output else 'hindsight-monitor.json'
    with open(json_output, 'w', encoding='utf-8') as f:
        json.dump({
            'health': health,
            'system': system,
            'bank_stats': bank_stats,
            'performance': performance
        }, f, ensure_ascii=False, indent=2)
    print(f"JSON 数据已保存到: {json_output}")


if __name__ == "__main__":
    main()
