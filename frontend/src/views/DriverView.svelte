<script>
  import { onMount, onDestroy } from 'svelte';
  import { api } from '../api.js';
  import { manifest, reload } from '../store.js';
  import { showToast } from '../toast.js';
  import SeatMapView from '../components/SeatMap.svelte';

  let operator = '司机老王';
  let filter = 'all';
  let timer;

  onMount(() => {
    timer = setInterval(reload, 5000);
    return () => clearInterval(timer);
  });

  $: m = $manifest;
  $: versionNo = m?.version?.versionNo ?? null;
  $: rows = (m?.assignments ?? []).filter(a => {
    if (filter === 'boarded') return a.boarded;
    if (filter === 'unconfirmed') return !a.boarded && a.status !== 'CONFIRMED';
    if (filter === 'confirmed') return !a.boarded && a.status === 'CONFIRMED';
    return true;
  });

  async function doBoard(a) {
    try {
      await api.board(a.passengerId, operator);
      await reload();
      showToast(`${a.name} 检票成功（v${versionNo}）`);
    } catch (e) {
      // 未确认新座位 -> 409；前端再明确提示一次
      showToast(`检票被拒绝：${e.message}`, true);
      await reload();
    }
  }

  async function doUnboard(a) {
    try {
      await api.unboard(a.passengerId);
      await reload();
      showToast(`已撤销 ${a.name} 的检票记录`);
    } catch (e) {
      showToast(e.message, true);
    }
  }

  $: unconfirmedCount = (m?.assignments ?? []).filter(a => !a.boarded && a.status !== 'CONFIRMED').length;
  $: unassignedCount = (m?.assignments ?? []).filter(a => a.status === 'UNASSIGNED').length;
  $: stats = m?.stats ?? {};
</script>

<div class="card">
  <h2>🚌 司机检票台 — {m?.vehicle?.code} {m?.vehicle?.name}</h2>
  <div class="row">
    <label>当班司机</label>
    <select bind:value={operator}>
      <option>司机老王</option>
      <option>司机老李</option>
    </select>
    <span class="pill-version">名单版本 v{versionNo}</span>
    <span class="muted">只能使用与乘客确认一致的最终名单</span>
    <span class="spacer"></span>
    <button class="btn ghost small" on:click={reload}>手动刷新名单</button>
  </div>

  {#if unconfirmedCount > 0 || unassignedCount > 0}
    <div class="driver-warn" style="margin-top:12px">
      🔒 有 {unconfirmedCount} 人尚未确认新座位、{unassignedCount} 人暂无座位（转人工中）——
      这些乘客<strong>不能检票</strong>，请提示其先在乘客页确认，或联系调度处理。
    </div>
  {:else}
    <div style="margin-top:12px" class="muted">✓ 全部已安排乘客均已确认，可以正常检票。</div>
  {/if}

  <div class="stat-grid" style="margin-top:12px">
    <div class="stat"><b>{stats.total ?? 0}</b><span>总人数</span></div>
    <div class="stat"><b>{stats.assigned ?? 0}</b><span>有座人数</span></div>
    <div class="stat"><b>{stats.confirmed ?? 0}</b><span>已确认</span></div>
    <div class="stat"><b>{stats.boarded ?? 0}</b><span>已检票</span></div>
  </div>
</div>

<div class="card">
  <div class="row" style="margin-bottom:10px">
    <h2 style="margin:0">检票名单</h2>
    <span class="spacer"></span>
    {#each [['all','全部'],['confirmed','可检票'],['unconfirmed','未确认/无座'],['boarded','已检票']] as [key, label]}
      <button class="btn small {filter === key ? '' : 'ghost'}" on:click={() => (filter = key)}>{label}</button>
    {/each}
  </div>
  <table>
    <thead>
      <tr><th>乘客</th><th>新座</th><th>同行人</th><th>确认情况</th><th>检票</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each rows as a}
        <tr>
          <td>{a.name}</td>
          <td>{#if a.newSeat}<strong>{a.newSeat}</strong>{:else}<span class="badge unassigned">无座</span>{/if}</td>
          <td>{#if a.groupName}<span class="badge group">{a.groupName}</span>{/if}</td>
          <td>
            {#if a.status === 'CONFIRMED'}<span class="badge confirmed">已确认</span>
            {:else if a.status === 'PROPOSED'}<span class="badge proposed">未确认</span>
            {:else}<span class="badge unassigned">转人工中</span>{/if}
          </td>
          <td>{#if a.boarded}<span class="badge boarded">已登车</span>{:else}<span class="muted">—</span>{/if}</td>
          <td>
            {#if a.boarded}
              <button class="btn small ghost" on:click={() => doUnboard(a)}>撤销检票</button>
            {:else if a.status === 'CONFIRMED'}
              <button class="btn small" on:click={() => doBoard(a)}>检票登车</button>
            {:else}
              <button class="btn small" disabled title="乘客未确认新座位或暂无座位，禁止检票">🚫 禁止检票</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

{#if m}
  <div class="card">
    <h2>车上实载座位图</h2>
    <SeatMapView seatMap={m.vehicle.seatMap} assignments={m.assignments} />
  </div>
{/if}
