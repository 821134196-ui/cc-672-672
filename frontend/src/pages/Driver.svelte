<script>
  import { onMount, onDestroy } from 'svelte'
  import { api } from '../api.js'

  let trips = []
  let tripId = null
  let manifest = null
  let error = ''
  let timer = null

  onMount(async () => {
    trips = await api.trips()
    if (trips.length) {
      tripId = trips[0].tripId
      await load()
    }
    // 轮询保证司机名单与乘客确认结果一致
    timer = setInterval(() => { if (tripId) load() }, 3000)
  })
  onDestroy(() => clearInterval(timer))

  async function load() {
    error = ''
    try {
      manifest = await api.manifest(tripId)
    } catch (e) {
      error = e.message
    }
  }

  async function checkIn(bookingId) {
    error = ''
    try {
      await api.checkIn(bookingId)
      await load()
    } catch (e) {
      error = e.message
      await load()
    }
  }
</script>

<div class="card">
  <div class="row">
    <label>班次
      <select bind:value={tripId} on:change={load}>
        {#each trips as t}
          <option value={t.tripId}>{t.code} {t.route}（{t.departureTime}）</option>
        {/each}
      </select>
    </label>
    <button class="ghost" on:click={load}>刷新</button>
  </div>
</div>

{#if error}<div class="alert error">{error}</div>{/if}

{#if manifest}
  <div class="card">
    <h2>
      最终登车名单 · {manifest.vehiclePlate}（{manifest.vehicleModel}） · 座位版本 v{manifest.versionNo}
    </h2>
    <p class="muted">仅包含乘客已确认的座位，与乘客端显示一致；未确认的乘客不能检票登车。</p>
    <table>
      <thead>
        <tr><th>姓名</th><th>座位</th><th>检票状态</th><th>操作</th></tr>
      </thead>
      <tbody>
        {#each manifest.confirmed as p}
          <tr>
            <td>{p.name}</td>
            <td><b>{p.seatNo}</b></td>
            <td>
              {#if p.checkedIn}
                <span class="badge checkedin">已登车（v{p.checkedInVersionNos.join(', v')}）</span>
              {:else}
                <span class="badge PENDING">未检票</span>
              {/if}
            </td>
            <td>
              {#if !p.checkedIn}
                <button on:click={() => checkIn(p.bookingId)}>检票登车</button>
              {/if}
            </td>
          </tr>
        {/each}
        {#if !manifest.confirmed.length}
          <tr><td colspan="4" class="muted">暂无已确认乘客</td></tr>
        {/if}
      </tbody>
    </table>
  </div>

  {#if manifest.notConfirmed.length}
    <div class="card">
      <h2>未确认乘客（不能检票）</h2>
      <table>
        <thead><tr><th>姓名</th><th>新座位</th><th>状态</th><th>备注</th></tr></thead>
        <tbody>
          {#each manifest.notConfirmed as b}
            <tr>
              <td>{b.name}</td>
              <td>{b.seatNo || '—'}</td>
              <td><span class="badge {b.status}">{b.status === 'PENDING' ? '待确认' : '转人工'}</span></td>
              <td class="muted">{b.note || ''}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
{/if}
