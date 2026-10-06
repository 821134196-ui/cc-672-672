<script>
  // 车辆座位图：按排渲染；普通座灰底，轮椅位紫、儿童位青，
  // 已占用蓝底并显示乘客简称，高亮乘客绿底。
  export let seatMap = [];
  export let assignments = []; // manifest.assignments
  export let highlightPassengerId = null;

  $: occupied = new Map(
    assignments.filter(a => a.newSeat).map(a => [a.newSeat, a])
  );

  $: rows = (() => {
    const byRow = new Map();
    for (const s of seatMap) {
      if (!byRow.has(s.row)) byRow.set(s.row, []);
      byRow.get(s.row).push(s);
    }
    return [...byRow.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([row, seats]) => [row, seats.sort((a, b) => a.order - b.order)]);
  })();

  const TYPE_LABEL = { WHEELCHAIR: '♿', CHILD: '🧒', STANDARD: '' };

  function shortName(name) {
    return name ? name.slice(-2) : '';
  }
</script>

<div class="bus-map">
  {#each rows as [rowNo, seats]}
    <div class="seat-row">
      <span class="rowno">{rowNo}排</span>
      {#each seats as s}
        {@const a = occupied.get(s.code)}
        {@const isMe = a && a.passengerId === highlightPassengerId}
        <div
          class="seat {a ? 'taken' : 'free'} {s.type === 'WHEELCHAIR' ? 'wheelchair' : ''} {s.type === 'CHILD' ? 'child' : ''} {isMe ? 'me' : ''} {s.col === 'B' ? 'aisle-gap' : ''}"
          title={a ? `${s.code} · ${a.name}${a.groupName ? '（' + a.groupName + '）' : ''}` : `${s.code} 空座`}
        >
          <span class="code">{s.code}</span>
          {#if a}<span>{shortName(a.name)}</span>{:else}<span>{TYPE_LABEL[s.type]}</span>{/if}
        </div>
      {/each}
    </div>
  {/each}
  <p class="muted" style="font-size:12px;margin-top:8px">
    <span class="badge standard">普通座</span>
    <span class="badge wheelchair">♿ 轮椅位</span>
    <span class="badge child">🧒 儿童座椅位</span>
    <span class="badge" style="background:var(--brand-soft);color:var(--brand)">已占用</span>
    <span class="badge" style="background:var(--ok-soft);color:var(--ok)">当前乘客</span>
  </p>
</div>
