// 轻量 API 客户端：所有请求走 Vite 代理 /api -> http://localhost:8080

async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!res.ok) {
    const msg = data && data.error ? data.error : `请求失败 (${res.status})`;
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  manifest: () => request('GET', '/api/manifest'),
  vehicles: () => request('GET', '/api/vehicles'),
  passengers: () => request('GET', '/api/passengers'),
  passenger: (id) => request('GET', `/api/passengers/${id}`),
  confirm: (id, versionNo) => request('POST', `/api/passengers/${id}/confirm`, { expectedVersionNo: versionNo }),
  swap: (payload) => request('POST', '/api/seat/swap', payload),
  board: (id, operator) => request('POST', `/api/boarding/${id}/board`, { operator }),
  unboard: (id) => request('POST', `/api/boarding/${id}/unboard`),
  versions: () => request('GET', '/api/versions'),
  versionDetail: (no) => request('GET', `/api/versions/${no}`),
  issues: () => request('GET', '/api/issues'),
  setIssueState: (id, state) => request('POST', `/api/issues/${id}/state`, { state }),
  sms: () => request('GET', '/api/mock/sms'),
  dispatchLogs: () => request('GET', '/api/mock/dispatch-logs'),
  reset: () => request('POST', '/api/admin/reset')
};
