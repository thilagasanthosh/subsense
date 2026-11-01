// Mirrors the backend REST API (see /swagger-ui.html). Money amounts are strings so they never
// pass through floating point.

export type CurrencyCode = 'GBP' | 'INR' | 'USD';
export type BillingCycle = 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
export type SubscriptionType = 'PAID' | 'FREE_TRIAL';
export type Decision = 'PENDING' | 'RENEW' | 'CANCEL' | 'DOWNGRADE';
export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'CANCELLING' | 'CANCELLED';
export type UsageRating = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'RARELY' | 'NEVER';
export type DecisionSource = 'UI' | 'MCP' | 'SYSTEM';

export const CURRENCIES: CurrencyCode[] = ['GBP', 'INR', 'USD'];
export const BILLING_CYCLES: BillingCycle[] = ['MONTHLY', 'QUARTERLY', 'YEARLY'];
export const USAGE_RATINGS: UsageRating[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'RARELY', 'NEVER'];

export interface Money {
  amount: string;
  currency: CurrencyCode;
}

export interface Category {
  id: number;
  name: string;
  colour: string;
}

export interface Subscription {
  id: number;
  name: string;
  vendor: string;
  category: Category;
  type: SubscriptionType;
  billingCycle: BillingCycle;
  price: Money;
  priceAfterTrial: Money | null;
  startDate: string;
  nextRenewalDate: string;
  noticeDays: number;
  cancelByDate: string;
  daysUntilRenewal: number;
  autoRenew: boolean;
  paymentLabel: string | null;
  owner: string;
  status: SubscriptionStatus;
  decision: Decision;
  downgradeAmount: Money | null;
  usageRating: UsageRating | null;
  notes: string | null;
  monthlyEquivalent: Money;
  monthlyEquivalentReporting: Money;
  annualCostReporting: Money;
}

/** What the add/edit form sends. */
export interface SubscriptionDetails {
  name: string;
  vendor: string;
  categoryId: number;
  type: SubscriptionType;
  billingCycle: BillingCycle;
  amount: string;
  priceAfterTrial: string | null;
  currency: CurrencyCode;
  startDate: string;
  nextRenewalDate: string;
  noticeDays: number;
  autoRenew: boolean;
  paymentLabel: string | null;
  owner: string;
  usageRating: UsageRating | null;
  notes: string | null;
}

export interface DecisionRequest {
  decision: Decision;
  newAmount?: string;
  reason?: string;
}

export interface DecisionEntry {
  decision: Decision;
  newAmount: Money | null;
  reason: string | null;
  source: DecisionSource;
  decidedAt: string;
}

export interface CategorySpend {
  category: string;
  colour: string;
  monthlySpend: Money;
}

export interface DashboardSummary {
  reportingCurrency: CurrencyCode;
  fxRatesAsOf: string;
  monthlySpend: Money;
  annualSpend: Money;
  activeCount: number;
  renewalsNext30Days: number;
  spendByCategory: CategorySpend[];
  needsDecision: Subscription[];
}
