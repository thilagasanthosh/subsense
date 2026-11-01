import { formatMoney, renewsIn, urgencyOf } from './format';

describe('formatMoney', () => {
  it('formats each currency in its own locale', () => {
    expect(formatMoney({ amount: '1234.50', currency: 'GBP' })).toBe('£1,234.50');
    expect(formatMoney({ amount: '1234.50', currency: 'USD' })).toBe('$1,234.50');
  });

  it('uses Indian digit grouping for rupees', () => {
    expect(formatMoney({ amount: '123456.00', currency: 'INR' })).toBe('₹1,23,456.00');
  });

  it('shows a dash when there is no amount', () => {
    expect(formatMoney(null)).toBe('–');
  });
});

describe('renewal wording and urgency', () => {
  it('describes how soon a renewal is', () => {
    expect(renewsIn(0)).toBe('Today');
    expect(renewsIn(1)).toBe('Tomorrow');
    expect(renewsIn(9)).toBe('In 9 days');
  });

  it('is urgent within a week and soon within a month', () => {
    expect(urgencyOf(7)).toBe('urgent');
    expect(urgencyOf(8)).toBe('soon');
    expect(urgencyOf(30)).toBe('soon');
    expect(urgencyOf(31)).toBe('later');
  });
});
