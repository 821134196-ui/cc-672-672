<script>
  import { onMount } from 'svelte';
  import { toast } from './toast.js';
  import { manifest, reload } from './store.js';
  import DispatchView from './views/DispatchView.svelte';
  import PassengerView from './views/PassengerView.svelte';
  import DriverView from './views/DriverView.svelte';

  let tab = 'dispatch';
  const tabs = [
    { key: 'dispatch', label: '调度台' },
    { key: 'passenger', label: '乘客页' },
    { key: 'driver', label: '司机页' }
  ];

  onMount(reload);

  // 切到乘客/司机页时也刷新一次，确保看到的是最新一致名单
  function switchTab(key) {
    tab = key;
    reload();
  }

  $: versionInfo = $manifest?.version ?? null;
</script>

<header class="topbar">
  <h1>🚌 临时换车座位重排系统</h1>
  {#if versionInfo}
    <span class="ver">
      当前座位版本 <span class="pill-version">v{versionInfo.versionNo}</span> · {versionInfo.reason}
    </span>
  {/if}
  <nav class="tabs">
    {#each tabs as t}
      <button class={tab === t.key ? 'active' : ''} on:click={() => switchTab(t.key)}>{t.label}</button>
    {/each}
  </nav>
</header>

<main>
  {#if tab === 'dispatch'}<DispatchView />
  {:else if tab === 'passenger'}<PassengerView />
  {:else}<DriverView />{/if}
</main>

{#if $toast}
  <div class="toast" class:err={$toast.isError}>⚠ {$toast.message}</div>
{/if}
