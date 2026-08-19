import '@testing-library/jest-dom/vitest';

/*
 * jsdom implements no layout, so `Element.scrollIntoView` simply does not exist on it.
 *
 * A component that keeps its active option in view is calling a real browser API correctly, and
 * making it defensive to satisfy the test environment would be the tail wagging the dog. Stubbed
 * here instead, once, for every test.
 */
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}
