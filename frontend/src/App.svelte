<script>
  import { onMount, onDestroy } from 'svelte'
  import Dispatcher from './pages/Dispatcher.svelte'
  import Passenger from './pages/Passenger.svelte'
  import Driver from './pages/Driver.svelte'

  const routes = {
    '/dispatcher': Dispatcher,
    '/passenger': Passenger,
    '/driver': Driver
  }

  let hash = window.location.hash.replace(/^#/, '') || '/dispatcher'

  function onHashChange() {
    hash = window.location.hash.replace(/^#/, '') || '/dispatcher'
  }

  onMount(() => {
    window.addEventListener('hashchange', onHashChange)
  })
  onDestroy(() => {
    window.removeEventListener('hashchange', onHashChange)
  })

  $: component = routes[hash] || Dispatcher
</script>

<div class="shell">
  <header>
    <h1>客运大巴临时换车座位重排系统</h1>
    <nav>
      <a href="#/dispatcher" class:active={hash === '/dispatcher'}>调度台</a>
      <a href="#/passenger" class:active={hash === '/passenger'}>乘客</a>
      <a href="#/driver" class:active={hash === '/driver'}>司机</a>
    </nav>
  </header>
  <main>
    <svelte:component this={component} />
  </main>
</div>
