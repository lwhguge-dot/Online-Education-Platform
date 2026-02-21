const apiBase = import.meta.env.VITE_API_BASE || '';
let wsOrigin = '';
if (typeof apiBase === 'string' && apiBase.startsWith('http')) {
  try {
    const u = new URL(apiBase);
    const wsProtocol = u.protocol === 'https:' ? 'wss:' : 'ws:';
    wsOrigin = `${wsProtocol}//${u.host}`;
  } catch {
    wsOrigin = '';
  }
}
if (!wsOrigin) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  wsOrigin = `${protocol}//${host}`;
}
const WS_BASE = `${wsOrigin}/ws/notification`;

let ws = null;
let reconnectTimer = null;
let heartbeatTimer = null;
let lastErrorLoggedAt = 0;
// 标记是否为前端主动断开，避免触发无意义的自动重连
let manualDisconnect = false;

const listeners = {
  onForceLogout: [],
  onNotification: [],
  onConnected: [],
  onDisconnected: [],
};

export const connectWebSocket = () => {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return;
  }

  const token = sessionStorage.getItem('token');
  if (!token) {
    console.warn('WebSocket 连接失败：缺少登录 token');
    return;
  }

  try {
    // 新连接建立前清理重连定时器，避免并发重连
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }

    manualDisconnect = false;
    // 握手 URL 不再拼接 token，避免 token 出现在 URL 日志中
    ws = new WebSocket(WS_BASE);

    ws.onopen = () => {
      console.log('WebSocket 连接成功');

      ws.send(JSON.stringify({
        type: 'AUTH',
        token,
      }));

      startHeartbeat();
      listeners.onConnected.forEach(fn => fn());
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        handleMessage(data);
      } catch (e) {
        console.error('解析 WebSocket 消息失败:', e);
      }
    };

    ws.onclose = () => {
      console.log('WebSocket 连接关闭');
      stopHeartbeat();
      listeners.onDisconnected.forEach(fn => fn());

      // 主动断开（如退出登录/页面卸载）时不重连
      if (!manualDisconnect) {
        reconnectTimer = setTimeout(() => {
          // 仅在会话仍有效时重连，避免登出后无意义重连
          if (sessionStorage.getItem('token')) {
            connectWebSocket();
          }
        }, 5000);
      }
    };

    ws.onerror = (error) => {
      const now = Date.now();
      if (now - lastErrorLoggedAt > 30000) {
        console.warn('WebSocket 连接异常:', error);
        lastErrorLoggedAt = now;
      }
    };
  } catch (e) {
    console.error('WebSocket 连接失败:', e);
  }
};

export const disconnectWebSocket = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }

  stopHeartbeat();

  if (ws) {
    manualDisconnect = true;
    ws.close();
    ws = null;
  }
};

const startHeartbeat = () => {
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'PING' }));
    }
  }, 30000);
};

const stopHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
};

const handleMessage = (data) => {
  switch (data.type) {
    case 'FORCE_LOGOUT':
      listeners.onForceLogout.forEach(fn => fn(data.reason));
      break;
    case 'NOTIFICATION':
      listeners.onNotification.forEach(fn => fn(data));
      break;
    case 'AUTH_OK':
      console.log('WebSocket 认证成功');
      break;
    case 'AUTH_FAILED':
      console.warn('WebSocket 认证失败');
      disconnectWebSocket();
      break;
    case 'PONG':
      break;
    default:
      console.log('未知消息类型:', data.type);
  }
};

export const onForceLogout = (callback) => {
  listeners.onForceLogout.push(callback);
  return () => {
    const index = listeners.onForceLogout.indexOf(callback);
    if (index > -1) {
      listeners.onForceLogout.splice(index, 1);
    }
  };
};

export const onNotification = (callback) => {
  listeners.onNotification.push(callback);
  return () => {
    const index = listeners.onNotification.indexOf(callback);
    if (index > -1) {
      listeners.onNotification.splice(index, 1);
    }
  };
};

export const onConnected = (callback) => {
  listeners.onConnected.push(callback);
  return () => {
    const index = listeners.onConnected.indexOf(callback);
    if (index > -1) {
      listeners.onConnected.splice(index, 1);
    }
  };
};

export const onDisconnected = (callback) => {
  listeners.onDisconnected.push(callback);
  return () => {
    const index = listeners.onDisconnected.indexOf(callback);
    if (index > -1) {
      listeners.onDisconnected.splice(index, 1);
    }
  };
};

export default {
  connectWebSocket,
  disconnectWebSocket,
  onForceLogout,
  onNotification,
  onConnected,
  onDisconnected,
};
