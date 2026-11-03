import React from 'react';
import TestRenderer, { act } from 'react-test-renderer';
import { ScrollView } from 'react-native';
import { initial } from '../src/core/flow';
import type { Flow, State } from '../src/core/types';
import catalog from '../src/data/catalog.json';
import { defaults, quote } from '../src/core/domain';
import type { Product } from '../src/core/types';
import { LinkApp } from '../App';
jest.mock('../src/services/useApp', () => ({ useApp: jest.fn() }));
jest.mock('../src/native/NativeLinkDevice', () => ({
  __esModule: true,
  default: { signal: jest.fn(), speak: jest.fn(), openSettings: jest.fn() },
}));
jest.mock('../src/services/api', () => ({ validateServer: (s: string) => s }));
import { useApp } from '../src/services/useApp';
const products = catalog.menu as Product[];
const p = products[0];
const cart = [
  { id: 'line', productId: p.id, quantity: 1, options: defaults(p) },
];
const q = quote(catalog.stores[0], products, cart);
const base: State = {
  ...initial,
  flow: 'V5-05',
  stores: catalog.stores,
  found: catalog.stores,
  products,
  storeId: 'kaist',
  selectedStoreId: 'kaist',
  productId: p.id,
  selected: defaults(p),
  cart,
  payment: { id: 'payment', state: 'ready', provider: 'offline', snapshot: q },
  order: {
    id: 'order',
    number: 'A-0001',
    paymentId: 'payment',
    snapshot: q,
    created: Date.now(),
    status: 'received',
    demo: true,
  },
  changed: q,
};
beforeEach(() => {
  jest.useFakeTimers();
});
afterEach(() => {
  jest.clearAllTimers();
  jest.useRealTimers();
});
function mockState(flow: Flow, overrides: Partial<State> = {}) {
  const s = { ...base, flow, history: { kaist: base.order! }, ...overrides };
  (useApp as jest.Mock).mockReturnValue({
    s,
    dispatch: jest.fn(),
    busy: false,
    reader: false,
    speak: jest.fn(),
    permission: jest.fn(),
    bluetooth: jest.fn(),
    voiceSearch: jest.fn(),
    complete: jest.fn(),
  });
}
test.each<Flow>([
  'V5-O1',
  'V5-O2',
  'V5-O3',
  'V5-O4',
  'V5-01',
  'E01',
  'E02',
  'E03',
  'E04',
  'E05',
  'V5-02',
  'V5-02e',
  'V5-02s',
  'V5-03',
  'V5-04',
  'V5-R1',
  'V5-M1',
  'V5-05',
  'V5-05c',
  'V5-06',
  'V5-06s',
  'V5-M3',
  'V5-M4',
  'V5-08e',
  'V5-08',
  'V5-09',
  'PAYMENT_EXTERNAL',
  'P01-E1',
  'P01-E2',
  'P02-C',
  'P02-F',
  'P02-T',
  'P02-V',
  'V5-10',
  'V5-11',
  'V5-12',
  'SETTINGS',
])('%s renders with fixed controls outside scrolling content', flow => {
  mockState(flow);
  let renderer: TestRenderer.ReactTestRenderer;
  act(() => {
    renderer = TestRenderer.create(<LinkApp />);
  });
  const footer = renderer!.root.findByProps({ testID: 'fixed-footer' });
  let ancestor = footer.parent;
  while (ancestor) {
    expect(ancestor.type).not.toBe(ScrollView);
    ancestor = ancestor.parent;
  }
  act(() => renderer!.unmount());
});
test('menu has a persistent cart action and selected options expose radio state', () => {
  mockState('V5-05c');
  let renderer: TestRenderer.ReactTestRenderer;
  act(() => {
    renderer = TestRenderer.create(<LinkApp />);
  });
  expect(
    renderer!.root.findByProps({ testID: 'primary-action' }).props.label,
  ).toContain('장바구니 보기');
  act(() => renderer!.unmount());
  mockState('V5-06');
  act(() => {
    renderer = TestRenderer.create(<LinkApp />);
  });
  const radios = renderer!.root.findAllByProps({ accessibilityRole: 'radio' });
  expect(radios.some(r => r.props.accessibilityState.selected)).toBe(true);
  act(() => renderer!.unmount());
});

test('reordering shows the new payment amount before the user confirms it', () => {
  const changedProducts = products.map(item =>
    item.id === p.id ? { ...item, price: item.price + 500 } : item,
  );
  mockState('V5-R1', { products: changedProducts });
  let renderer: TestRenderer.ReactTestRenderer;
  act(() => {
    renderer = TestRenderer.create(<LinkApp />);
  });
  const newAmount = (q.total + 500).toLocaleString('ko-KR') + '원';
  expect(
    renderer!.root.findByProps({ testID: 'primary-action' }).props.label,
  ).toBe(`${newAmount} 결제`);
  expect(
    renderer!.root.findAllByProps({
      label: '현재 결제 금액',
      value: newAmount,
    }),
  ).toHaveLength(1);
  act(() => renderer!.unmount());
});
