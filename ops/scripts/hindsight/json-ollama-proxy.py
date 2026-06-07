import json
import urllib.request
import urllib.error
import os
import socketserver
from http.server import HTTPServer, BaseHTTPRequestHandler

OLLAMA_HOST = os.environ.get("OLLAMA_HOST", "127.0.0.1")
OLLAMA_PORT = int(os.environ.get("OLLAMA_PORT", "11434"))
PROXY_PORT = int(os.environ.get("PROXY_PORT", "11435"))
LLM_TIMEOUT = int(os.environ.get("LLM_TIMEOUT", "600"))


class ProxyHandler(BaseHTTPRequestHandler):

    def _forward(self, body: bytes, path: str) -> tuple[bytes, int]:
        url = f"http://{OLLAMA_HOST}:{OLLAMA_PORT}{path}"
        data = json.loads(body)

        if path.endswith("/chat/completions"):
            if "response_format" not in data:
                data["response_format"] = {"type": "json_object"}
            if "temperature" not in data:
                data["temperature"] = 0

        req = urllib.request.Request(
            url,
            data=json.dumps(data, ensure_ascii=False).encode(),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=LLM_TIMEOUT) as resp:
            return resp.read(), resp.status

    def do_POST(self):
        cl = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(cl) if cl else b"{}"
        try:
            resp_body, status = self._forward(body, self.path)
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(resp_body)
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())

    def do_GET(self):
        url = f"http://{OLLAMA_HOST}:{OLLAMA_PORT}{self.path}"
        try:
            with urllib.request.urlopen(url, timeout=30) as resp:
                self.send_response(resp.status)
                self.send_header("Content-Type", resp.headers.get("Content-Type", "application/json"))
                self.end_headers()
                self.wfile.write(resp.read())
        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())

    def log_message(self, fmt, *args):
        path = args[0] if args else ""
        if path and "/chat/completions" not in path:
            return
        super().log_message(fmt, *args)


if __name__ == "__main__":
    server = socketserver.ThreadingTCPServer(("0.0.0.0", PROXY_PORT), ProxyHandler)
    server.timeout = 0.5
    print(f"JSON proxy listening on port {PROXY_PORT} → {OLLAMA_HOST}:{OLLAMA_PORT} (timeout={LLM_TIMEOUT}s)")
    server.serve_forever()
