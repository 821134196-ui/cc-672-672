import { writable } from 'svelte/store';

export const toast = writable(null);

let timer;
export function showToast(message, isError = false) {
  toast.set({ message, isError });
  clearTimeout(timer);
  timer = setTimeout(() => toast.set(null), 4000);
}
