const BASE = '/api'

async function req(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    let msg = `请求失败 (${res.status})`
    try {
      const body = await res.json()
      if (body && body.message) msg = body.message
    } catch { /* ignore */ }
    const err = new Error(msg)
    err.status = res.status
    throw err
  }
  return res.json()
}

export const api = {
  // 调度
  trips: () => req('/dispatcher/trips'),
  trip: (id) => req(`/dispatcher/trips/${id}`),
  versions: (id) => req(`/dispatcher/trips/${id}/versions`),
  availableVehicles: (id) => req(`/dispatcher/trips/${id}/available-vehicles`),
  changeVehicle: (id, vehicleId, operator) =>
    req(`/dispatcher/trips/${id}/change-vehicle`, {
      method: 'POST',
      body: JSON.stringify({ vehicleId, operator })
    }),
  seatMap: (versionId) => req(`/dispatcher/versions/${versionId}/seats`),
  manualAssign: (versionId, bookingId, seatNo) =>
    req(`/dispatcher/versions/${versionId}/assign`, {
      method: 'POST',
      body: JSON.stringify({ bookingId, seatNo })
    }),
  // 乘客
  passenger: (bookingId) => req(`/passenger/bookings/${bookingId}`),
  confirm: (bookingId) => req(`/passenger/bookings/${bookingId}/confirm`, { method: 'POST' }),
  // 检票 / 司机
  checkIn: (bookingId) => req('/checkins', { method: 'POST', body: JSON.stringify({ bookingId }) }),
  manifest: (tripId) => req(`/driver/trips/${tripId}/manifest`),
  // 模拟
  smsLog: () => req('/mock/sms')
}
