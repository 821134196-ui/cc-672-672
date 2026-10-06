import { writable, get } from 'svelte/store';
import { api } from './api.js';

/**
 * 全局名单 store：调度台、乘客页、司机页读的是同一份 manifest，
 * 从数据源上保证“乘客和司机看到的座位结果保持一致”。
 * 任何写操作（换车/确认/检票/处理问题）后调用 reload() 即可同步三视图。
 */
export const manifest = writable(null);
export const loading = writable(false);

let reloading = false;

export async function reload() {
  if (reloading) return;
  reloading = true;
  loading.set(true);
  try {
    const m = await api.manifest();
    manifest.set(m);
  } finally {
    reloading = false;
    loading.set(false);
  }
}

export function currentVersionNo() {
  const m = get(manifest);
  return m?.version?.versionNo ?? null;
}

export function assignmentOf(passengerId) {
  const m = get(manifest);
  return m?.assignments?.find(a => a.passengerId === passengerId) ?? null;
}
