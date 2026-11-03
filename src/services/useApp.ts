import { useEffect, useReducer, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  AppState,
  BackHandler,
  Platform,
} from 'react-native';
import { initial, reduce, snapshot } from '../core/flow';
import { sameQuote } from '../core/domain';
import type { Payment } from '../core/types';
import Device from '../native/NativeLinkDevice';
import { persist, readSnapshot } from './storage';
import * as api from './api';

export function useApp() {
  const [s, dispatch] = useReducer(reduce, initial);
  const live = useRef(s);
  live.current = s;
  const [busy, setBusy] = useState(false);
  const [reader, setReader] = useState(false);
  const completing = useRef(false);
  useEffect(() => {
    let active = true;
    readSnapshot()
      .then(saved => {
        if (active)
          dispatch({
            type: 'HYDRATE',
            snapshot: saved,
            stores: api.stores,
            products: api.products,
          });
      })
      .catch(() => {
        if (active) {
          dispatch({
            type: 'HYDRATE',
            stores: api.stores,
            products: api.products,
          });
          dispatch({
            type: 'FAIL',
            flow: 'E04',
            message:
              '기기에 저장된 내용을 읽지 못했어요. 저장 공간을 확인해 주세요.',
          });
        }
      });
    AccessibilityInfo.isScreenReaderEnabled().then(setReader);
    const listener = AccessibilityInfo.addEventListener(
      'screenReaderChanged',
      setReader,
    );
    return () => {
      active = false;
      listener.remove();
      Device?.cancelScan();
      Device?.stopSpeaking();
    };
  }, []);
  useEffect(() => {
    if (s.flow !== 'BOOT')
      persist(snapshot(s)).catch(() =>
        dispatch({
          type: 'FAIL',
          flow: 'E04',
          message:
            '기기에 주문 내용을 저장하지 못했어요. 저장 공간을 확인해 주세요.',
        }),
      );
  }, [
    s.onboarded,
    s.settings,
    s.storeId,
    s.cart,
    s.history,
    s.request,
    s.payment,
    s.order,
  ]);
  useEffect(() => {
    if (s.signal) Device?.signal(s.signal);
  }, [s.signal, s.revision]);
  useEffect(() => {
    const h = BackHandler.addEventListener('hardwareBackPress', () => {
      if (['BOOT', 'V5-01', 'V5-O1'].includes(live.current.flow)) return false;
      dispatch({ type: 'BACK' });
      return true;
    });
    const a = AppState.addEventListener('change', state => {
      if (state !== 'active') Device?.cancelScan();
      else if (['V5-01', 'E01', 'E02'].includes(live.current.flow))
        dispatch({ type: 'DISCOVER', demo: live.current.discovery === 'demo' });
      else if (live.current.flow === 'PAYMENT_EXTERNAL')
        dispatch({ type: 'NAV', flow: 'P02-V' });
    });
    return () => {
      h.remove();
      a.remove();
    };
  }, []);
  useEffect(() => {
    let cancelled = false;
    const state = s;
    const send = (action: Parameters<typeof dispatch>[0]) => {
      if (!cancelled) dispatch(action);
    };
    async function run() {
      if (state.flow === 'V5-01') {
        try {
          const stores = await api.getStores(state.settings);
          if (cancelled) return;
          // Server store metadata is needed for selection and authoritative quoting.
          send({ type: 'STORES', stores });
          if (state.discovery === 'demo') {
            send({ type: 'FOUND', stores });
            return;
          }
          if (Platform.OS !== 'android' || !Device) {
            send({
              type: 'FAIL',
              flow: 'E02',
              message:
                '이 기기에서는 매장 자동 탐색을 사용할 수 없어요. 매장 이름 검색이나 데모를 이용해 주세요.',
            });
            return;
          }
          const readiness = await Device.readiness();
          if (cancelled) return;
          if (readiness !== 'ready') {
            send({
              type: 'FAIL',
              flow: readiness === 'permission' ? 'E01' : 'E02',
              message:
                readiness === 'permission'
                  ? '매장을 찾으려면 주변 기기 권한을 허용해 주세요. Android 11 이하는 위치 권한이 필요해요.'
                  : readiness === 'location'
                  ? '위치 기능을 켠 뒤 다시 찾아 주세요.'
                  : '블루투스를 켠 뒤 다시 찾아 주세요.',
            });
            return;
          }
          const found = JSON.parse(
            await Device.scan(JSON.stringify(stores.map(x => x.beaconName))),
          ) as { name: string; rssi: number }[];
          send({
            type: 'FOUND',
            stores: found.flatMap(x =>
              stores.filter(st => st.beaconName === x.name),
            ),
          });
        } catch (e) {
          if (!cancelled)
            send({ type: 'FAIL', flow: 'E04', message: message(e) });
        }
      } else if (state.flow === 'V5-04') {
        try {
          send({
            type: 'MENU_LOADED',
            products: await api.getMenu(state.settings, state.storeId!),
          });
        } catch (e) {
          send({
            type: 'FAIL',
            flow: e instanceof api.ApiError && e.code === 409 ? 'E05' : 'E04',
            message: message(e),
          });
        }
      } else if (state.flow === 'V5-09' || state.flow === 'P02-V') {
        let submitted = state.flow === 'P02-V' || !!state.payment;
        try {
          await persist(snapshot(state));
          if (cancelled) return;
          let p = state.payment;
          if (!p && state.request) {
            const fresh =
              state.flow === 'V5-09'
                ? await api.revalidate(state.settings, state.request)
                : undefined;
            if (cancelled) return;
            if (fresh && !sameQuote(fresh, state.request.expected)) {
              send({
                type: 'PRICE_CHANGED',
                quote: fresh,
                products: await api.getMenu(
                  state.settings,
                  state.request.storeId,
                ),
              });
              return;
            }
            submitted = true;
            p = await api.prepare(state.settings, state.request);
            if (cancelled) return;
          }
          if (!p) {
            send({
              type: 'FAIL',
              flow: 'P01-E2',
              message:
                '진행 중인 결제 정보가 없어요. 장바구니에서 다시 확인해 주세요.',
            });
            return;
          }
          if (state.flow === 'V5-09') {
            send({ type: 'PAYMENT_READY', payment: p });
            return;
          }
          p = await api.getPayment(state.settings, p.id);
          if (cancelled) return;
          const order = p.orderId
            ? await api.getOrder(state.settings, p.orderId)
            : undefined;
          send({ type: 'PAYMENT_RESULT', payment: p, order });
        } catch (e) {
          if (
            e instanceof api.ApiError &&
            e.reason === 'PRICE_CHANGED' &&
            state.request
          ) {
            try {
              const fresh = await api.revalidate(state.settings, state.request);
              send({
                type: 'PRICE_CHANGED',
                quote: fresh,
                products: await api.getMenu(
                  state.settings,
                  state.request.storeId,
                ),
              });
            } catch (failure) {
              send({ type: 'FAIL', flow: 'P02-V', message: message(failure) });
            }
          } else
            send({
              type: 'FAIL',
              flow: submitted ? 'P02-V' : 'P01-E2',
              message: message(e),
            });
        }
      } else if (
        state.flow === 'PAYMENT_EXTERNAL' &&
        state.payment?.provider.startsWith('kakao')
      ) {
        try {
          await persist(snapshot(state));
          if (cancelled) return;
          const url = state.payment.redirect;
          if (!url || !Device)
            throw new Error(
              '결제 화면을 열 수 없어요. Android에서 다시 시도해 주세요.',
            );
          await Device.openPayment(url, state.settings.serverUrl);
          send({ type: 'NAV', flow: 'P02-V' });
        } catch (e) {
          send({ type: 'FAIL', flow: 'P02-V', message: message(e) });
        }
      }
    }
    void run();
    return () => {
      cancelled = true;
      if (state.flow === 'V5-01') Device?.cancelScan();
    };
    // revision marks explicit transitions/retries, not text input or persistence updates.
  }, [s.flow, s.revision]);
  useEffect(() => {
    if (!s.order || !['V5-10', 'V5-11', 'V5-12'].includes(s.flow)) return;
    let active = true;
    const poll = () =>
      api
        .getOrder(s.settings, s.order!.id)
        .then(order => {
          if (active) dispatch({ type: 'ORDER_UPDATED', order });
        })
        .catch(() => {});
    void poll();
    const timer = setInterval(poll, 3000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [s.flow, s.order?.id, s.settings]);
  const speak = (text: string, force = false) => {
    if (force || (s.settings.voice && !reader)) Device?.speak(text);
  };
  async function permission() {
    try {
      if (await Device?.requestPermissions()) dispatch({ type: 'DISCOVER' });
      else
        dispatch({
          type: 'FAIL',
          flow: 'E01',
          message:
            '권한이 거부됐어요. 설정에서 주변 기기 권한을 허용해 주세요.',
        });
    } catch (e) {
      dispatch({ type: 'FAIL', flow: 'E01', message: message(e) });
    }
  }
  async function bluetooth() {
    try {
      await Device?.requestBluetooth();
      dispatch({ type: 'DISCOVER' });
    } catch (e) {
      dispatch({ type: 'FAIL', flow: 'E02', message: message(e) });
    }
  }
  async function voiceSearch() {
    try {
      const text = await Device?.recognize();
      if (text) dispatch({ type: 'QUERY', value: text });
    } catch (e) {
      dispatch({ type: 'FAIL', flow: live.current.flow, message: message(e) });
    }
  }
  async function complete(result: 'success' | 'cancel' | 'fail' | 'timeout') {
    if (completing.current || !live.current.payment) return;
    completing.current = true;
    setBusy(true);
    const state = live.current;
    try {
      await persist(snapshot(state));
      const p: Payment = await api.complete(
        state.settings,
        state.payment!.id,
        result,
      );
      const order = p.orderId
        ? await api.getOrder(state.settings, p.orderId)
        : undefined;
      dispatch({ type: 'PAYMENT_RESULT', payment: p, order });
    } catch (e) {
      dispatch({ type: 'FAIL', flow: 'P02-V', message: message(e) });
    } finally {
      completing.current = false;
      setBusy(false);
    }
  }
  return {
    s,
    dispatch,
    busy,
    reader,
    speak,
    permission,
    bluetooth,
    voiceSearch,
    complete,
  };
}
function message(e: unknown) {
  return e instanceof Error ? e.message : '다시 시도해 주세요.';
}
