import socket, urllib.request, json, sys

print("=== DNS Check ===")
try:
    ip = socket.gethostbyname('host.docker.internal')
    print('host.docker.internal resolves to:', ip)
except Exception as e:
    print('host.docker.internal DNS FAIL:', e)

print("\n=== Proxy Check ===")
try:
    req = urllib.request.Request('http://localhost:11435/api/tags', method='GET')
    with urllib.request.urlopen(req, timeout=15) as resp:
        d = json.loads(resp.read())
        models = [m['name'] for m in d.get('models', [])]
        print('Proxy models:', models)
except Exception as e:
    print('Proxy FAIL:', type(e).__name__, e)

print("\n=== Direct Ollama Check ===")
try:
    req = urllib.request.Request('http://host.docker.internal:11434/api/tags', method='GET')
    with urllib.request.urlopen(req, timeout=15) as resp:
        d = json.loads(resp.read())
        models = [m['name'] for m in d.get('models', [])]
        print('Direct models:', models)
except Exception as e:
    print('Direct FAIL:', type(e).__name__, e)

print("\n=== Chat Completion via Proxy ===")
try:
    body = json.dumps({'model': 'hindsight-json', 'messages': [{'role': 'user', 'content': 'hi'}], 'stream': False})
    req = urllib.request.Request(
        'http://localhost:11435/v1/chat/completions',
        data=body.encode(),
        headers={'Content-Type': 'application/json'},
        method='POST',
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        d = json.loads(resp.read())
        msg = d['choices'][0]['message']['content']
        print('Chat OK:', msg[:200])
except Exception as e:
    print('Chat FAIL:', type(e).__name__, e)

sys.stdout.flush()
