<script>
  import { onMount, onDestroy } from 'svelte';
  import { api } from '../api.js';
  import { manifest, reload } from '../store.js';
  import { showToast } from '../toast.js';
  import SeatMapView from '../components/SeatMap.svelte';

  let passengers = [];
  let selectedId = null;
  let detail = null;
  let confirming = false;
  let timer;

  onMount(async () => {
    passengers = await api.passengers();
    if (passengers.length) selectedId = passengers[0].id;
    await loadDetail();
    // 乘客页每 5 秒同步一次名单，保证与司机/调度看到的一致
    timer = setInterval(async () => {
      await reload();
      await loadDetail();
    }, 5000);
  });
  onDestroy(() => clearInterval(timer));

  async function loadDetail() {
    if (selectedId != null) {
      detail = await api.passenger(selectedId);
    }
  }

  async function selectPassenger(id) {
    selectedId = id;
    await loadDetail();
  }

  async function confirmSeat() {
    confirming = true;
    try {
      detail = await api.confirm(selectedId, detail.versionNo);
      await reload();
      showToast('新座位已确认，司机端检票名单已同步');
    } catch (e) {
      showToast(e.message, true);
      await Promise.all([reload(), loadDetail()]);
    } finally {
      confirming = false;
    }
  }

  $: m = $manifest;
</script>

<div class="card">
  <h2>🙋 乘客确认页</h2>
  <div class="passenger-pick">
    <label>选择乘客（模拟本人打开确认链接）</label>
    <select value={selectedId} on:change={e => selectPassenger(Number(e.target.value))}>
      {#each passengers as p}
        <option value={p.id}>{p.name} · 原座 {p.originalSeat}{p.groupName ? ' · ' + p.groupName : ''}</option>
      {/each}
    </select>
    {#if detail?.specialNeed && detail.specialNeed !== 'NONE'}
      <span class="badge {detail.specialNeed === 'WHEELCHAIR' ? 'wheelchair' : 'child'}">
        {detail.specialNeed === 'WHEELCHAIR' ? '♿ 轮椅乘客' : '🧒 需要儿童座椅'}
      </span>
    {/if}
    {#if detail?.groupName}<span class="badge group">同行：{detail.groupName}</span>{/if}
  </div>

  {#if detail?.versionNo}
    <p class="muted">
      因“{detail.reason}”，车辆更换为 <strong>{detail.vehicleCode} · {detail.vehicleName}</strong>
      （座位版本 v{detail.versionNo}）。请核对您的新座位并确认。
    </p>
  {/if}

  {#if detail}
    <div class="big-seat-cmp">
      <div class="box">
        <div class="label">原座位（票面）</div>
        <div class="seatno">{detail.originalSeat}</div>
        <div class="muted">不会变更，仅作对照</div>
      </div>
      <div class="box newbox">
        <div class="label">新座位（{detail.vehicleCode ?? ''}）</div>
        {#if detail.newSeat}
          <div class="seatno">{detail.newSeat}</div>
          <div>
            {#if detail.seatType === 'WHEELCHAIR'}<span class="badge wheelchair">♿ 轮椅位</span>
            {:else if detail.seatType === 'CHILD'}<span class="badge child">🧒 儿童座椅位</span>
            {:else}<span class="badge standard">普通座</span>{/if}
          </div>
        {:else}
          <div class="seatno none">暂无座位</div>
          <div class="muted">您的特殊需求在新车上无法自动满足，已转人工处理，请等候工作人员联系</div>
        {/if}
      </div>
    </div>

    {#if detail.status === 'PROPOSED'}
      <div class="confirm-note">
        ⚠ 您尚未确认新座位。在确认之前，司机无法为您检票登车。
      </div>
      <button class="btn" on:click={confirmSeat} disabled={confirming || !detail.newSeat}>
        {confirming ? '提交中…' : '我确认新座位无误'}
      </button>
    {:else if detail.status === 'CONFIRMED'}
      <div class="row">
        <span class="badge confirmed">✓ 您已于 {detail.confirmedAt?.slice(0, 19).replace('T', ' ')} 确认新座位</span>
        {#if detail.boarded}
          <span class="badge boarded">🎫 已检票登车</span>
        {:else}
          <span class="muted">请凭此确认结果上车检票</span>
        {/if}
      </div>
    {:else if detail.status === 'UNASSIGNED'}
      <div class="driver-warn">暂无可用座位：本次换车已为您转人工处理，请勿前往检票口，等候工作人员安排。</div>
    {/if}
  {/if}
</div>

{#if m && detail}
  <div class="card">
    <h2>新车座位图（绿色为您的座位）</h2>
    <SeatMapView seatMap={m.vehicle.seatMap} assignments={m.assignments} highlightPassengerId={selectedId} />
  </div>
{/if}
