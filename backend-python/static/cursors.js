// static/cursors.js
(function () {
  const WS_BASE = (() => {
    const port = location.port;
    if (port === '' || port === '443' || port === '80') {
      return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`;
    }
    return `ws://${location.hostname}:8001`;
  })();

  window.connectCursors = function (tierlistId) {
    const color = '#' + Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0');
    const cursors = {};
    const url = `${WS_BASE}/ws/cursors?tierlist_id=${tierlistId || 0}`;
    const ws = new WebSocket(url);

    ws.onmessage = (e) => {
      const data = JSON.parse(e.data);
      if (data.type === 'leave') return removeCursor(data.clientId);
      updateCursor(data);
    };
    ws.onclose = () => console.warn('[cursors] disconnected');

    function updateCursor(data) {
      let cur = cursors[data.clientId];
      if (!cur) {
        const el = document.createElement('div');
        el.className = 'remote-cursor';
        el.innerHTML = `
          <svg width="20" height="20" viewBox="0 0 24 24">
            <path d="M5 2.5 L5 19 L9.2 15.2 L11.8 21 L14.6 19.7 L12 14 L18 13.8 Z"
                  fill="${data.color || '#f00'}" stroke="#fff"
                  stroke-width="1.4" stroke-linejoin="round"/>
          </svg>
          <div class="remote-label" style="background:${data.color || '#f00'}">
            ${data.username || data.clientId}
          </div>`;
        document.body.appendChild(el);
        cur = cursors[data.clientId] = { el };
      }
      cur.el.style.left = data.x + 'px';
      cur.el.style.top  = data.y + 'px';
    }
    function removeCursor(id) {
      if (cursors[id]) { cursors[id].el.remove(); delete cursors[id]; }
    }

    let lastSent = 0;
    document.addEventListener('mousemove', (e) => {
      if (ws.readyState !== WebSocket.OPEN) return;
      const now = Date.now();
      if (now - lastSent < 33) return;
      lastSent = now;
      ws.send(JSON.stringify({ x: e.clientX, y: e.clientY, color }));
    });
  };
})();