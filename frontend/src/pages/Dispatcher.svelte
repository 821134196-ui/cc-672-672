<script>
  import { onMount } from 'svelte'
  import { api } from '../api.js'

  const STATUS = { CONFIRMED: '已确认', PENDING: '待确认', MANUAL_REVIEW: '转人工' }
  const NEED = { NONE: '', WHEELCHAIR: '轮椅', CHILD_SEAT: '儿童座椅' }

  let trips = []
  let tripId = null
  let detail = null
  let versions = []
  let vehicles = []
  let seatMap = null
  let smsLog = []
  let showSms = false
  let warnings = []
  let error = ''
  let operator = '调度员A'
  let manualBookingId = ''
  let manualSeatNo = ''

  onMount(async () => {
    trips = await api.trips()
    if (trips.length) {
      tripId = trips[0].tripId
      await reload()
    }
  })

  async function reload() {
    error = ''
    ;[detail, versions, vehicles] = await Promise.all([
      api.trip(tripId),
      api.versions(tripId),
      api.availableVehicles(tripId)
    ])
    const active = versions.find((v) => v.status === 'ACTIVE')
    seatMap = active ? await api.seatMap(active.versionId) : null
  }

  async function changeVehicle(vehicleId) {
    warnings = []
    error = ''
    try {
      const result = await api.changeVehicle(tripId, vehicleId, operator)
      warnings = result.warnings
      await reload()
    } catch (e) {
      error = e.message
    }
  }

  async function manualAssign() {
    error = ''
    const active = versions.find((v) => v.status === 'ACTIVE')
    if (!active || !manualBookingId || !manualSeatNo) return
    try {
      await api.manualAssign(active.versionId, Number(manualBookingId), manualSeatNo.trim().toUpperCase())
      manualSeatNo = ''
      await reload()
    } catch (e) {
      error = e.message
    }
  }

  async function toggleSms() {
    showSms = !showSms
    if (showSms) smsLog = await api.smsLog()
  }

  $: rows = seatMap
    ? Object.values(
        seatMap.seats.reduce((acc, s) => {
          ;(acc[s.rowIdx] = acc[s.rowIdx] || []).push(s)
          return acc
        }, {})
      )
    : []
</script>

<div class="row" style="margin-bottom:12px">
  <label>班次
    <select bind:value={tripId} on:change={reload}>
      {#each trips as t}
        <option value={t.tripId}>{t.code} {t.route}（{t.departureTime}）</option>
      {/each}
    </select>
  </label>
  <label>操作人 <input bind:value={operator} style="width:90px" /></label>
  <button class="ghost" on:click={reload}>刷新</button>
  <button class="ghost" on:click={toggleSms}>{showSms ? '隐藏' : '查看'}短信模拟</button>
</div>

{#if error}<div class="alert error">{error}</div>{/if}
{#each warnings as w}
  <div class="alert warn">⚠ {w}</div>
{/each}

{#if detail}
  <div class="card">
    <h2>当前车辆：{detail.activeVehiclePlate}（{detail.activeVehicleModel}） · 座位版本 v{detail.activeVersionNo}</h2>
    <div class="row">
      {#each vehicles as v}
        <div class="card" style="margin:0; padding:10px 14px">
          <div><b>{v.plateNumber}</b> {v.model}</div>
          <div class="muted">
            共 {v.totalSeats} 座 · 普通 {v.normalSeats} · 轮椅位 {v.wheelchairSeats} · 儿童座椅位 {v.childSeatSeats}
          </div>
          <button style="margin-top:6px" on:click={() => changeVehicle(v.vehicleId)}>换到此车</button>
        </div>
      {/each}
      {#if !vehicles.length}<span class="muted">车辆调度接口：暂无可用备用车</span>{/if}
    </div>
  </div>

  <div class="card">
    <h2>乘客与座位分配</h2>
    <table>
      <thead>
        <tr>
          <th>姓名</th><th>同行组</th><th>特殊需求</th><th>原座位</th><th>新座位</th><th>状态</th><th>备注</th>
        </tr>
      </thead>
      <tbody>
        {#each detail.bookings as b}
          <tr>
            <td>{b.name} {#if b.checkedIn}<span class="badge checkedin">已检票</span>{/if}</td>
            <td>{b.groupId || '—'}</td>
            <td>{#if NEED[b.need]}<span class="badge need">{NEED[b.need]}</span>{:else}—{/if}</td>
            <td>{b.originalSeatNo}</td>
            <td><b>{b.seatNo || '—'}</b></td>
            <td>
              <span class="badge {b.status}">{STATUS[b.status] || b.status}</span>
              {#if b.groupSplit}<span class="badge split">同行人被拆散</span>{/if}
            </td>
            <td class="muted">{b.note || ''}</td>
          </tr>
        {/each}
      </tbody>
    </table>
    <div class="row" style="margin-top:12px">
      <span class="muted">手动调座：</span>
      <select bind:value={manualBookingId}>
        <option value="">选择乘客</option>
        {#each detail.bookings as b}
          <option value={b.bookingId}>{b.name}（{b.seatNo || '无座'}）</option>
        {/each}
      </select>
      <input placeholder="目标座位号 如 4C" bind:value={manualSeatNo} style="width:140px" />
      <button on:click={manualAssign} disabled={!manualBookingId || !manualSeatNo}>调整</button>
    </div>
  </div>
{/if}

{#if seatMap}
  <div class="card">
    <h2>座位图 · v{seatMap.versionNo} · {seatMap.vehiclePlate}（{seatMap.vehicleModel}）</h2>
    <div class="seatmap">
      {#each rows as row}
        <div class="seat-row">
          <span class="rowno">{row[0].rowIdx}</span>
          {#each row as s, i}
            {#if i === 2}<span class="seat aisle"></span>{/if}
            <div class="seat {s.kind} {s.occupiedBy ? 'occupied-' + s.status : ''}">
              <span>{s.seatNo}</span>
              <span class="who">{s.occupiedBy || (s.kind === 'WHEELCHAIR' ? '轮椅位' : s.kind === 'CHILD_SEAT' ? '儿童座椅' : '')}</span>
            </div>
          {/each}
        </div>
      {/each}
    </div>
  </div>
{/if}

{#if versions.length}
  <div class="card">
    <h2>座位版本历史（再次换车旧版本保留）</h2>
    <table>
      <thead><tr><th>版本</th><th>车辆</th><th>状态</th><th>操作人</th><th>时间</th></tr></thead>
      <tbody>
        {#each versions as v}
          <tr>
            <td>v{v.versionNo}</td>
            <td>{v.vehiclePlate}（{v.vehicleModel}）</td>
            <td>{v.status === 'ACTIVE' ? '当前生效' : '已替换'}</td>
            <td>{v.operator || '—'}</td>
            <td class="muted">{v.createdAt}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

{#if showSms}
  <div class="card">
    <h2>短信发送记录（本地模拟）</h2>
    <table>
      <thead><tr><th>手机号</th><th>内容</th><th>时间</th></tr></thead>
      <tbody>
        {#each smsLog as m}
          <tr><td>{m.phone}</td><td>{m.content}</td><td class="muted">{m.createdAt}</td></tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
