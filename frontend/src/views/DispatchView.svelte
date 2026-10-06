<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { manifest, reload } from '../store.js';
  import { showToast } from '../toast.js';
  import SeatMapView from '../components/SeatMap.svelte';

  let vehicles = [];
  let selectedVehicle = '';
  let operator = '调度甲';
  let reason = '';
  let swapping = false;
  let showLogs = false;
  let smsLogs = [];
  let dispatchLogs = [];
  let versions = [];
  let viewVersionNo = null; // 查看历史版本时使用；null 表示当前版本
  let historyManifest = null;

  onMount(loadVehicles);
  $: m = $manifest;
  $: currentNo = m?.version?.versionNo ?? null;
  $: stats = m?.stats ?? { total: 0, assigned: 0, confirmed: 0, boarded: 0, openIssues: 0 };

  async function loadVehicles() {
    vehicles = await api.vehicles();
  }

  async function doSwap() {
    if (!selectedVehicle) {
      showToast('请先选择一辆新车', true);
      return;
    }
    swapping = true;
    try {
      await api.swap({
        vehicleCode: selectedVehicle,
        expectedVersionNo: currentNo,
        operator,
        reason: reason || undefined
      });
      reason = '';
      await reload();
      const latest = $manifest;
      const openIssues = latest.issues.filter(i => i.state === 'OPEN');
      if (openIssues.length === 0) {
        showToast(`换车成功，已生成座位版本 v${latest.version.versionNo}，已向乘客发送短信通知`);
      } else {
        showToast(`已生成 v${latest.version.versionNo}：${openIssues.length} 个问题需要关注（见问题列表）`);
      }
    } catch (e) {
      showToast(e.message, true);
      await reload(); // 409 时刷新版本号
    } finally {
      swapping = false;
    }
  }

  async function resolveIssue(id, state) {
    try {
      await api.setIssueState(id, state);
      await reload();
      showToast(state === 'ESCALATED' ? '已标记转人工处理' : '问题已解决');
    } catch (e) {
      showToast(e.message, true);
    }
  }

  async function toggleLogs() {
    showLogs = !showLogs;
    if (showLogs) {
      [smsLogs, dispatchLogs, versions] = await Promise.all([
        api.sms(), api.dispatchLogs(), api.versions()
      ]);
    }
  }

  async function viewVersion(no) {
    if (no === currentNo || no === null) {
      viewVersionNo = null;
      historyManifest = null;
    } else {
      viewVersionNo = no;
      historyManifest = await api.versionDetail(no);
    }
  }

  async function resetDemo() {
    if (!confirm('确定重置全部演示数据？将恢复初始车辆与 v1 座位。')) return;
    await api.reset();
    viewVersionNo = null;
    historyManifest = null;
    await reload();
    showToast('演示数据已重置');
  }

  $: shownManifest = historyManifest ?? m;
  $: shownIssues = shownManifest?.issues ?? [];
  const ISSUE_LABEL = {
    WHEELCHAIR_UNMET: '轮椅位无法满足 · 转人工',
    CHILD_SEAT_UNMET: '儿童座椅无法满足 · 转人工',
    GROUP_SPLIT: '同行人被拆散',
    OVERFLOW: '座位不足 · 超载'
  };
</script>

<div class="card">
  <h2>🚦 临时换车与重排</h2>
  <div class="row">
    <label>调度员</label>
    <select bind:value={operator}>
      <option>调度甲</option>
      <option>调度乙</option>
    </select>
    <label>选择新车</label>
    <select bind:value={selectedVehicle}>
      <option value="">— 请选择可调配车辆 —</option>
      {#each vehicles as v}
        <option value={v.code}>{v.code} · {v.name}（{v.seatCount} 座）</option>
      {/each}
    </select>
    <input placeholder="换车原因（选填），如：原车故障" bind:value={reason} style="min-width:240px" />
    <button class="btn" on:click={doSwap} disabled={swapping}>
      {swapping ? '重排中…' : '执行换车并重新排座'}
    </button>
    <span class="spacer"></span>
    <button class="btn ghost small" on:click={toggleLogs}>{showLogs ? '收起' : '查看'}版本/短信/调度记录</button>
    <button class="btn ghost small" on:click={resetDemo}>重置演示数据</button>
  </div>
  <p class="muted" style="margin:10px 0 0;font-size:12px">
    并发保护：换车必须携带当前版本号，两名调度同时操作时后提交者会收到 409 冲突并需刷新，
    数据库唯一约束再做兜底，保证不会出现两个活动名单或一座两人。
  </p>
</div>

{#if showLogs}
  <div class="card">
    <h2>📜 座位版本历史（连续换车全程保留）</h2>
    <div class="row" style="margin-bottom:10px">
      <button class="btn ghost small" on:click={() => viewVersion(null)}>查看当前 v{currentNo}</button>
      {#each versions as v}
        <button
          class="btn small {v.status === 'ACTIVE' ? '' : 'ghost'}"
          on:click={() => viewVersion(v.versionNo)}
        >
          v{v.versionNo} {v.vehicleCode} {v.status === 'ACTIVE' ? '●当前' : ''}
        </button>
      {/each}
      {#if viewVersionNo !== null}<span class="muted">正在查看历史版本 v{viewVersionNo}（只读）</span>{/if}
    </div>
    <div class="row" style="align-items:flex-start">
      <div style="flex:1;min-width:320px">
        <h3>📤 模拟短信接口（最近 100 条）</h3>
        <div style="max-height:240px;overflow:auto">
          <table>
            <thead><tr><th>时间</th><th>手机</th><th>版本</th><th>内容</th></tr></thead>
            <tbody>
              {#each smsLogs as s}
                <tr><td class="mono" style="font-size:11px">{s.sentAt.slice(0, 19)}</td><td>{s.phone}</td><td>v{s.versionNo}</td><td style="white-space:normal">{s.content}</td></tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>
      <div style="flex:1;min-width:320px">
        <h3>🚍 模拟车辆调度工单</h3>
        <div style="max-height:240px;overflow:auto">
          <table>
            <thead><tr><th>时间</th><th>从</th><th>到</th><th>版本</th><th>操作人</th></tr></thead>
            <tbody>
              {#each dispatchLogs as l}
                <tr><td class="mono" style="font-size:11px">{l.createdAt.slice(0, 19)}</td><td>{l.fromVehicle || '—'}</td><td>{l.toVehicle}</td><td>v{l.versionNo}</td><td>{l.operator}</td></tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
{/if}

<div class="card">
  <h2>⚠ 重排问题（{shownIssues.length}）</h2>
  {#if shownIssues.length === 0}
    <p class="muted">本版本没有待处理问题，所有已安排乘客均已按规则入座。</p>
  {/if}
  {#each shownIssues as issue}
    <div class="issue-card {issue.type === 'GROUP_SPLIT' ? 'split' : ''}">
      <div class="row">
        <span class="badge {issue.state === 'OPEN' ? 'open' : issue.state === 'RESOLVED' ? 'resolved' : 'escalated'}">{issue.state}</span>
        <strong>{ISSUE_LABEL[issue.type]}</strong>
        <span class="muted">v{issue.versionNo}</span>
        <span class="spacer"></span>
        {#if issue.state === 'OPEN' && viewVersionNo === null}
          <button class="btn small ghost" on:click={() => resolveIssue(issue.id, 'RESOLVED')}>人工已解决</button>
          <button class="btn small danger" on:click={() => resolveIssue(issue.id, 'ESCALATED')}>转人工处理</button>
        {/if}
      </div>
      <div class="detail">{issue.detail}</div>
    </div>
  {/each}
</div>

<div class="card">
  <div class="row">
    <h2 style="margin:0">🪑 {shownManifest?.vehicle?.code} {shownManifest?.vehicle?.name} 座位图</h2>
    <span class="spacer"></span>
    <div class="stat-grid">
      <div class="stat"><b>{stats.total}</b><span>已售票</span></div>
      <div class="stat"><b>{stats.assigned}</b><span>已排座</span></div>
      <div class="stat"><b>{stats.confirmed}</b><span>乘客已确认</span></div>
      <div class="stat"><b>{stats.boarded}</b><span>已检票</span></div>
      <div class="stat"><b style="color:var(--danger)">{stats.openIssues}</b><span>待处理问题</span></div>
    </div>
  </div>
  {#if shownManifest}
    <SeatMapView seatMap={shownManifest.vehicle.seatMap} assignments={shownManifest.assignments} />
  {/if}
</div>

<div class="card">
  <h2>📋 名单（原座 → 新座 对照）</h2>
  <table>
    <thead>
      <tr>
        <th>乘客</th><th>同行人</th><th>特殊需求</th><th>原座</th><th>新座</th>
        <th>乘客确认</th><th>检票</th>
      </tr>
    </thead>
    <tbody>
      {#each shownManifest?.assignments ?? [] as a}
        <tr>
          <td>{a.name}</td>
          <td>{#if a.groupName}<span class="badge group">{a.groupName}</span>{/if}</td>
          <td>
            {#if a.specialNeed === 'WHEELCHAIR'}<span class="badge wheelchair">♿ 轮椅</span>
            {:else if a.specialNeed === 'CHILD_SEAT'}<span class="badge child">🧒 儿童座椅</span>{/if}
          </td>
          <td class="muted">{a.originalSeat}</td>
          <td>
            {#if a.newSeat}
              <strong>{a.newSeat}</strong>
              {#if a.seatType === 'WHEELCHAIR'}<span class="badge wheelchair">♿</span>
              {:else if a.seatType === 'CHILD'}<span class="badge child">🧒</span>{/if}
            {:else}<span class="badge unassigned">无座 · 待人工</span>{/if}
          </td>
          <td>
            {#if a.status === 'CONFIRMED'}<span class="badge confirmed">已确认</span>
            {:else if a.status === 'PROPOSED'}<span class="badge proposed">待确认</span>
            {:else}<span class="badge unassigned">无法安排</span>{/if}
          </td>
          <td>{#if a.boarded}<span class="badge boarded">已登车</span>{:else}<span class="muted">—</span>{/if}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
