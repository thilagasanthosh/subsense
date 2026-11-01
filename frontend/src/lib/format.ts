import type { BillingCycle, CurrencyCode, Money, SubscriptionStatus } from '../api/types';

const LOCALES: Record<CurrencyCode, string> = { GBP: 'en-GB', INR: 'en-IN', USD: 'en-US' };

/** Formats money in its own locale: £1,234.50, ₹1,23,456.00, $1,234.50. */
export function formatMoney(money: Money | null | undefined): string {
  if (!money) {
    return '–';
  }
  return new Intl.NumberFormat(LOCALES[money.currency], {
    style: 'currency',
    currency: money.currency,
  }).format(Number(money.amount));
}

export function formatDate(isoDate: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${isoDate}T00:00:00`));
}

export function renewsIn(days: number): string {
  if (days < 0) return `${-days} day${days === -1 ? '' : 's'} ago`;
  if (days === 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  return `In ${days} days`;
}

export type Urgency = 'urgent' | 'soon' | 'later';

/** Red within a week, amber within a month. */
export function urgencyOf(days: number): Urgency {
  if (days <= 7) return 'urgent';
  if (days <= 30) return 'soon';
  return 'later';
}

export const CYCLE_LABELS: Record<BillingCycle, string> = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  YEARLY: 'Yearly',
};

export const STATUS_LABELS: Record<SubscriptionStatus, string> = {
  TRIAL: 'Free trial',
  ACTIVE: 'Active',
  CANCELLING: 'Cancelling',
  CANCELLED: 'Cancelled',
};

export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ');
}
