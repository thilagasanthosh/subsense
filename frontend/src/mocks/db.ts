// In-memory sample data for `npm run dev:mock` and tests. All values are fictional examples.
// The real calculations live in the backend; this mirrors them just enough to make the UI realistic.
import type {
  BillingCycle,
  Category,
  CurrencyCode,
  DashboardSummary,
  DecisionEntry,
  DecisionRequest,
  Money,
  Subscription,
  SubscriptionDetails,
} from '../api/types';

export const categories: Category[] = [
  { id: 1, name: 'AI Tools', colour: '#6366F1' },
  { id: 2, name: 'Career', colour: '#0EA5E9' },
  { id: 3, name: 'Entertainment', colour: '#EC4899' },
  { id: 4, name: 'Household', colour: '#8B5CF6' },
  { id: 5, name: 'Security', colour: '#10B981' },
  { id: 6, name: 'Shopping', colour: '#F59E0B' },
  { id: 7, name: 'Utilities', colour: '#64748B' },
];

const RATES_AS_OF = '2026-10-30';
const PER_EURO: Record<CurrencyCode, number> = { GBP: 0.86, INR: 101.2, USD: 1.16 };
const MONTHS: Record<BillingCycle, number> = { MONTHLY: 1, QUARTERLY: 3, YEARLY: 12 };
const REPORTING: CurrencyCode = 'GBP';

const toMoney = (amount: number, currency: CurrencyCode): Money => ({
  amount: amount.toFixed(2),
  currency,
});

const convert = (amount: number, from: CurrencyCode, to: CurrencyCode) =>
  (amount / PER_EURO[from]) * PER_EURO[to];

function isoDaysFromToday(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return localIso(d);
}

function localIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function daysUntil(iso: string): number {
  const today = new Date(`${localIso(new Date())}T00:00:00Z`);
  return Math.round((new Date(`${iso}T00:00:00Z`).getTime() - today.getTime()) / 86_400_000);
}

interface Row extends SubscriptionDetails {
  id: number;
  decision: Subscription['decision'];
  downgradeAmount: string | null;
  cancelledOn?: string;
}

let nextId = 1;
let rows: Row[] = [];
let decisions: Record<number, DecisionEntry[]> = {};

function sample(
  name: string,
  vendor: string,
  categoryId: number,
  cycle: BillingCycle,
  amount: string,
  currency: CurrencyCode,
  renewsInDays: number,
  extra: Partial<Row> = {},
): Row {
  return {
    id: nextId++,
    name,
    vendor,
    categoryId,
    type: 'PAID',
    billingCycle: cycle,
    amount,
    priceAfterTrial: null,
    currency,
    startDate: '2025-06-01',
    nextRenewalDate: isoDaysFromToday(renewsInDays),
    noticeDays: 0,
    autoRenew: true,
    paymentLabel: 'Visa',
    owner: 'Me',
    usageRating: 'WEEKLY',
    notes: null,
    decision: 'PENDING',
    downgradeAmount: null,
    ...extra,
  };
}

export function resetDb() {
  nextId = 1;
  decisions = {};
  rows = [
    sample('Claude Pro', 'Anthropic', 1, 'MONTHLY', '18.00', 'GBP', 3, { usageRating: 'DAILY' }),
    sample('ChatGPT Plus', 'OpenAI', 1, 'MONTHLY', '20.00', 'USD', 9, { usageRating: 'RARELY' }),
    sample('Netflix Standard', 'Netflix', 3, 'MONTHLY', '12.99', 'GBP', 12),
    sample('LinkedIn Premium', 'LinkedIn', 2, 'MONTHLY', '0', 'GBP', 6, {
      type: 'FREE_TRIAL',
      priceAfterTrial: '29.99',
      paymentLabel: 'PayPal',
    }),
    sample('Amazon Prime', 'Amazon', 6, 'YEARLY', '95.00', 'GBP', 41),
    sample('TV Licence', 'TV Licensing', 4, 'QUARTERLY', '43.63', 'GBP', 27, {
      paymentLabel: 'Direct Debit',
    }),
    sample('Antivirus Plus', 'McAfee', 5, 'YEARLY', '89.99', 'GBP', 19, {
      decision: 'CANCEL',
      noticeDays: 7,
    }),
    sample('Streaming India', 'JioHotstar', 3, 'YEARLY', '1499.00', 'INR', 64, {
      paymentLabel: 'UPI',
      owner: 'Family',
    }),
    sample('Mobile plan', 'Jio', 7, 'MONTHLY', '349.00', 'INR', 22, { paymentLabel: 'UPI' }),
    sample('Disney+', 'Disney', 3, 'MONTHLY', '7.99', 'GBP', 0, {
      decision: 'CANCEL',
      cancelledOn: isoDaysFromToday(0),
    }),
  ];
}

resetDb();

function view(r: Row): Subscription {
  const category = categories.find((c) => c.id === r.categoryId) ?? categories[0]!;
  const recurring = Number(r.type === 'FREE_TRIAL' ? r.priceAfterTrial : r.amount);
  const monthly = recurring / MONTHS[r.billingCycle];
  const monthlyReporting = convert(monthly, r.currency, REPORTING);
  const cancelBy = new Date(`${r.nextRenewalDate}T00:00:00Z`);
  cancelBy.setUTCDate(cancelBy.getUTCDate() - r.noticeDays);
  return {
    id: r.id,
    name: r.name,
    vendor: r.vendor,
    category,
    type: r.type,
    billingCycle: r.billingCycle,
    price: toMoney(Number(r.amount), r.currency),
    priceAfterTrial: r.priceAfterTrial ? toMoney(Number(r.priceAfterTrial), r.currency) : null,
    startDate: r.startDate,
    nextRenewalDate: r.nextRenewalDate,
    noticeDays: r.noticeDays,
    cancelByDate: cancelBy.toISOString().slice(0, 10),
    daysUntilRenewal: daysUntil(r.nextRenewalDate),
    autoRenew: r.autoRenew,
    paymentLabel: r.paymentLabel,
    owner: r.owner,
    status: r.cancelledOn
      ? 'CANCELLED'
      : r.decision === 'CANCEL'
        ? 'CANCELLING'
        : r.type === 'FREE_TRIAL'
          ? 'TRIAL'
          : 'ACTIVE',
    cancelledOn: r.cancelledOn ?? null,
    decision: r.decision,
    downgradeAmount: r.downgradeAmount ? toMoney(Number(r.downgradeAmount), r.currency) : null,
    usageRating: r.usageRating,
    notes: r.notes,
    monthlyEquivalent: toMoney(monthly, r.currency),
    monthlyEquivalentReporting: toMoney(monthlyReporting, REPORTING),
    annualCostReporting: toMoney(monthlyReporting * 12, REPORTING),
  };
}

export const db = {
  list: (): Subscription[] =>
    rows.map(view).sort((a, b) => a.nextRenewalDate.localeCompare(b.nextRenewalDate)),

  get: (id: number): Subscription | undefined => {
    const row = rows.find((r) => r.id === id);
    return row && view(row);
  },

  save: (details: SubscriptionDetails, id?: number): Subscription => {
    if (id === undefined) {
      const row: Row = { ...details, id: nextId++, decision: 'PENDING', downgradeAmount: null };
      rows.push(row);
      return view(row);
    }
    const index = rows.findIndex((r) => r.id === id);
    rows[index] = { ...rows[index]!, ...details };
    return view(rows[index]!);
  },

  remove: (id: number) => {
    rows = rows.filter((r) => r.id !== id);
  },

  decide: (id: number, request: DecisionRequest): Subscription | undefined => {
    const row = rows.find((r) => r.id === id);
    if (!row) return undefined;
    row.decision = request.decision;
    row.downgradeAmount = request.decision === 'DOWNGRADE' ? (request.newAmount ?? null) : null;
    (decisions[id] ??= []).unshift({
      decision: request.decision,
      newAmount: row.downgradeAmount ? toMoney(Number(row.downgradeAmount), row.currency) : null,
      reason: request.reason ?? null,
      source: 'UI',
      decidedAt: new Date().toISOString(),
    });
    return view(row);
  },

  decisions: (id: number): DecisionEntry[] => decisions[id] ?? [],

  dashboard: (): DashboardSummary => {
    const charging = db.list().filter((s) => s.status !== 'CANCELLING' && s.status !== 'CANCELLED');
    const monthly = charging.reduce(
      (sum, s) => sum + Number(s.monthlyEquivalentReporting.amount),
      0,
    );
    const byCategory = new Map<string, { colour: string; total: number }>();
    for (const s of charging) {
      const entry = byCategory.get(s.category.name) ?? { colour: s.category.colour, total: 0 };
      entry.total += Number(s.monthlyEquivalentReporting.amount);
      byCategory.set(s.category.name, entry);
    }
    return {
      reportingCurrency: REPORTING,
      fxRatesAsOf: RATES_AS_OF,
      monthlySpend: toMoney(monthly, REPORTING),
      annualSpend: toMoney(monthly * 12, REPORTING),
      activeCount: charging.length,
      renewalsNext30Days: charging.filter(
        (s) => s.daysUntilRenewal >= 0 && s.daysUntilRenewal <= 30,
      ).length,
      spendByCategory: [...byCategory.entries()]
        .sort(([, a], [, b]) => b.total - a.total)
        .map(([category, { colour, total }]) => ({
          category,
          colour,
          monthlySpend: toMoney(total, REPORTING),
        })),
      needsDecision: charging.filter(
        (s) => s.decision === 'PENDING' && s.daysUntilRenewal >= 0 && s.daysUntilRenewal <= 14,
      ),
    };
  },
};
