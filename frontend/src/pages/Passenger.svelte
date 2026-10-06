<script>
  import { onMount, onDestroy } from 'svelte'
  import { api } from '../api.js'

  const STATUS = { CONFIRMED: '已确认', PENDING: '待确认', MANUAL_REVIEW: '转人工处理' }

  let bookingId = ''
  let view = null
  let error = ''
  let timer = null

  onMount(() => {
    // 每 3 秒自动刷新，保证乘客看到的结果与调度/司机一致
    timer = setInterval(() => { if (view) load() }, 3000)
  })
  onDestroy(() => clearInterval(timer))

  async function load() {
    if (!bookingId) return
    error = ''
    try {
      view = await api.passenger(Number(bookingId))
    } catch (e) {
      error = e.message
      view = null
    }
  }

  async function confirm() {
    error = ''
    try {
      view = await api.confirm(view.bookingId)
    } catch (e) {
      error = e.message
    }
  }
</script>

<div class="card">
  <h2>查询我的座位</h2>
  <div class="row">
    <input placeholder="订单号（演示数据为 1–16）" bind:value={bookingId} style="width:220px" />
    <button on:click={load} disabled={!bookingId}>查询</button>
    <span class="muted">换车后请在此确认新座位；未确认前不能检票登车</span>
  </div>
</div>

{#if error}<div class="alert error">{error}</div>{/if}

{#if view}
  <div class="card">
    <h2>{view.name} · {view.tripCode} {view.route}（{view.departureTime}）</h2>
    <p class="muted">当前车辆：{view.vehiclePlate}（{view.vehicleModel}） · 座位版本 v{view.versionNo}</p>

    <div class="compare">
      <div class="side">
        <div class="muted">原座位</div>
        <div class="seatno">{view.originalSeatNo}</div>
      </div>
      <div class="arrow">→</div>
      <div class="side">
        <div class="muted">新座位</div>
        <div class="seatno">{view.newSeatNo || '待安排'}</div>
        <span class="badge {view.status}">{STATUS[view.status] || view.status}</span>
      </div>
    </div>

    {#if view.note}
      <div class="alert {view.status === 'MANUAL_REVIEW' ? 'error' : 'warn'}" style="margin-top:12px">{view.note}</div>
    {/if}
    {#if view.groupSplit}
      <div class="alert warn">您的同行人未能安排相邻座位，如有需要请联系工作人员。</div>
    {/if}

    <div class="row" style="margin-top:16px">
      {#if view.status === 'PENDING'}
        <button on:click={confirm}>确认新座位</button>
      {:else if view.status === 'CONFIRMED'}
        <div class="alert ok" style="margin:0">✓ 已确认，凭此座位登车 {#if view.checkedIn}（已检票）{/if}</div>
      {:else if view.status === 'MANUAL_REVIEW'}
        <div class="alert warn" style="margin:0">您的特殊需求正在人工处理，请稍候或联系现场工作人员。</div>
      {/if}
    </div>
  </div>
{/if}
