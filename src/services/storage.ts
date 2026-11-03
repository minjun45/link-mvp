import AsyncStorage from '@react-native-async-storage/async-storage';
import type { Snapshot } from '../core/types';
let pending: Promise<unknown> = Promise.resolve();
export async function readSnapshot(): Promise<Snapshot | undefined> {
  const text = await AsyncStorage.getItem('link-rn.state.v1');
  if (!text) return;
  try {
    return JSON.parse(text);
  } catch {
    return;
  }
}
export function persist(value: Snapshot): Promise<void> {
  const encoded = JSON.stringify(value);
  const next = pending
    .catch(() => {})
    .then(() => AsyncStorage.setItem('link-rn.state.v1', encoded));
  pending = next;
  return next;
}
export function serial<T>(work: () => Promise<T>): Promise<T> {
  const next = pending.catch(() => {}).then(work);
  pending = next;
  return next;
}
