import { z } from 'zod';
import type { Subscription, SubscriptionDetails } from '../api/types';

const money = z
  .string()
  .trim()
  .regex(/^\d{1,10}(\.\d{1,2})?$/, 'Enter an amount like 9.99');

/** Same rules as the backend, so most mistakes are caught before a round trip. */
export const subscriptionFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(100),
    vendor: z.string().trim().min(1, 'Vendor is required').max(100),
    categoryId: z.string().min(1, 'Choose a category'),
    type: z.enum(['PAID', 'FREE_TRIAL']),
    billingCycle: z.enum(['MONTHLY', 'QUARTERLY', 'YEARLY']),
    amount: z.string().trim(),
    priceAfterTrial: z.string().trim(),
    currency: z.enum(['GBP', 'INR', 'USD']),
    startDate: z.string().min(1, 'Start date is required'),
    nextRenewalDate: z.string().min(1, 'Renewal date is required'),
    noticeDays: z
      .string()
      .regex(/^\d{1,3}$/, 'Enter 0 to 365 days')
      .transform(Number)
      .pipe(z.number().max(365, 'Enter 0 to 365 days')),
    autoRenew: z.boolean(),
    paymentLabel: z
      .string()
      .trim()
      .max(50)
      .refine((v) => (v.match(/\d/g) ?? []).length <= 4, 'Use a label like "Visa ending 4417"'),
    owner: z.string().trim().min(1, 'Owner is required').max(50),
    usageRating: z.string(),
    notes: z.string().max(1000),
  })
  .refine((v) => !v.startDate || !v.nextRenewalDate || v.nextRenewalDate >= v.startDate, {
    path: ['nextRenewalDate'],
    message: 'Must be on or after the start date',
  })
  .refine((v) => v.type !== 'PAID' || money.safeParse(v.amount).success, {
    path: ['amount'],
    message: 'Enter an amount like 9.99',
  })
  .refine((v) => v.type !== 'FREE_TRIAL' || money.safeParse(v.priceAfterTrial).success, {
    path: ['priceAfterTrial'],
    message: 'Enter the price you will pay after the trial',
  });

export type SubscriptionFormInput = z.input<typeof subscriptionFormSchema>;
export type SubscriptionFormValues = z.output<typeof subscriptionFormSchema>;

export const emptyForm: SubscriptionFormInput = {
  name: '',
  vendor: '',
  categoryId: '',
  type: 'PAID',
  billingCycle: 'MONTHLY',
  amount: '',
  priceAfterTrial: '',
  currency: 'GBP',
  startDate: new Date().toISOString().slice(0, 10),
  nextRenewalDate: '',
  noticeDays: '0',
  autoRenew: true,
  paymentLabel: '',
  owner: 'Me',
  usageRating: '',
  notes: '',
};

export function toFormInput(s: Subscription): SubscriptionFormInput {
  return {
    name: s.name,
    vendor: s.vendor,
    categoryId: String(s.category.id),
    type: s.type,
    billingCycle: s.billingCycle,
    amount: s.price.amount,
    priceAfterTrial: s.priceAfterTrial?.amount ?? '',
    currency: s.price.currency,
    startDate: s.startDate,
    nextRenewalDate: s.nextRenewalDate,
    noticeDays: String(s.noticeDays),
    autoRenew: s.autoRenew,
    paymentLabel: s.paymentLabel ?? '',
    owner: s.owner,
    usageRating: s.usageRating ?? '',
    notes: s.notes ?? '',
  };
}

const emptyToNull = (value: string) => (value === '' ? null : value);

export function toDetails(v: SubscriptionFormValues): SubscriptionDetails {
  const trial = v.type === 'FREE_TRIAL';
  return {
    name: v.name,
    vendor: v.vendor,
    categoryId: Number(v.categoryId),
    type: v.type,
    billingCycle: v.billingCycle,
    amount: trial ? '0' : v.amount,
    priceAfterTrial: trial ? v.priceAfterTrial : null,
    currency: v.currency,
    startDate: v.startDate,
    nextRenewalDate: v.nextRenewalDate,
    noticeDays: v.noticeDays,
    autoRenew: v.autoRenew,
    paymentLabel: emptyToNull(v.paymentLabel),
    owner: v.owner,
    usageRating: emptyToNull(v.usageRating) as SubscriptionDetails['usageRating'],
    notes: emptyToNull(v.notes),
  };
}

const MONTHS = { MONTHLY: 1, QUARTERLY: 3, YEARLY: 12 } as const;

/** Live preview only; the backend works out the authoritative figures. */
export function previewMonthly(amount: string, cycle: keyof typeof MONTHS): string | null {
  if (!/^\d+(\.\d{1,2})?$/.test(amount)) return null;
  return (Number(amount) / MONTHS[cycle]).toFixed(2);
}
