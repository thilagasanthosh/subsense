import { emptyForm, previewMonthly, subscriptionFormSchema, toDetails } from './subscriptionForm';

const valid = {
  ...emptyForm,
  name: 'Claude Pro',
  vendor: 'Anthropic',
  categoryId: '1',
  amount: '18.00',
  startDate: '2026-01-15',
  nextRenewalDate: '2026-11-15',
};

const errorsFor = (input: typeof valid) => {
  const result = subscriptionFormSchema.safeParse(input);
  return result.success
    ? {}
    : Object.fromEntries(result.error.issues.map((i) => [i.path[0], i.message]));
};

describe('subscription form rules', () => {
  it('accepts a complete paid subscription', () => {
    expect(errorsFor(valid)).toEqual({});
  });

  it('rejects a renewal date before the start date', () => {
    expect(errorsFor({ ...valid, nextRenewalDate: '2026-01-01' })).toEqual({
      nextRenewalDate: 'Must be on or after the start date',
    });
  });

  it('requires a price after the trial for a free trial', () => {
    expect(errorsFor({ ...valid, type: 'FREE_TRIAL', amount: '' })).toEqual({
      priceAfterTrial: 'Enter the price you will pay after the trial',
    });
  });

  it('refuses anything that looks like a card number', () => {
    expect(errorsFor({ ...valid, paymentLabel: '4111 1111 1111 1111' })).toHaveProperty(
      'paymentLabel',
    );
    expect(errorsFor({ ...valid, paymentLabel: 'Visa ending 4417' })).toEqual({});
  });

  it('sends a free trial as zero now and its later price', () => {
    const parsed = subscriptionFormSchema.parse({
      ...valid,
      type: 'FREE_TRIAL',
      amount: '',
      priceAfterTrial: '29.99',
    });
    expect(toDetails(parsed)).toMatchObject({
      amount: '0',
      priceAfterTrial: '29.99',
      noticeDays: 0,
    });
  });
});

describe('previewMonthly', () => {
  it('spreads quarterly and yearly prices over the months', () => {
    expect(previewMonthly('43.50', 'QUARTERLY')).toBe('14.50');
    expect(previewMonthly('95.00', 'YEARLY')).toBe('7.92');
  });

  it('shows nothing for an incomplete amount', () => {
    expect(previewMonthly('9.', 'MONTHLY')).toBeNull();
  });
});
