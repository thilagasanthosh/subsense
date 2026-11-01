import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useCategories, useSaveSubscription, useSubscription } from '../api/hooks';
import { BILLING_CYCLES, CURRENCIES, USAGE_RATINGS } from '../api/types';
import { QueryState } from '../components/QueryState';
import { CYCLE_LABELS, formatDate, formatMoney, titleCase } from '../lib/format';
import {
  emptyForm,
  previewMonthly,
  subscriptionFormSchema,
  toDetails,
  toFormInput,
  type SubscriptionFormInput,
  type SubscriptionFormValues,
} from '../lib/subscriptionForm';

export function SubscriptionFormPage() {
  const params = useParams();
  const id = params.id ? Number(params.id) : undefined;
  return id === undefined ? <SubscriptionForm /> : <EditSubscription id={id} />;
}

function EditSubscription({ id }: { id: number }) {
  const { data, isLoading, error } = useSubscription(id);
  return (
    <QueryState isLoading={isLoading} error={error}>
      {data && <SubscriptionForm id={id} initial={toFormInput(data)} />}
    </QueryState>
  );
}

interface FormProps {
  id?: number;
  initial?: SubscriptionFormInput;
}

export function SubscriptionForm({ id, initial = emptyForm }: FormProps) {
  const navigate = useNavigate();
  const { data: categories = [] } = useCategories();
  const save = useSaveSubscription(id);
  const {
    register,
    handleSubmit,
    control,
    setError,
    setValue,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<SubscriptionFormInput, unknown, SubscriptionFormValues>({
    resolver: zodResolver(subscriptionFormSchema),
    defaultValues: initial,
  });

  // Default to the first category once they load, for a new subscription.
  useEffect(() => {
    const first = categories[0];
    if (first && !getValues('categoryId')) {
      setValue('categoryId', String(first.id));
    }
  }, [categories, getValues, setValue]);

  const [type, amount, priceAfterTrial, cycle, currency, renewal, noticeDays] = useWatch({
    control,
    name: [
      'type',
      'amount',
      'priceAfterTrial',
      'billingCycle',
      'currency',
      'nextRenewalDate',
      'noticeDays',
    ],
  });
  const trial = type === 'FREE_TRIAL';
  const monthly = previewMonthly(trial ? priceAfterTrial : amount, cycle);
  const cancelBy = cancelByPreview(renewal, noticeDays);

  const onSubmit = async (values: SubscriptionFormValues) => {
    try {
      const saved = await save.mutateAsync(toDetails(values));
      navigate(`/subscriptions/${saved.id}`);
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
        Object.entries(e.fieldErrors).forEach(([field, message]) =>
          setError(field as keyof SubscriptionFormInput, { message }),
        );
      } else {
        setError('root', { message: e instanceof Error ? e.message : 'Could not save' });
      }
    }
  };

  const fieldError = (name: keyof SubscriptionFormInput) => {
    const message = errors[name]?.message;
    return message ? (
      <span className="error" role="alert">
        {message}
      </span>
    ) : null;
  };

  return (
    <>
      <header className="page-header">
        <div>
          <h1>{id === undefined ? 'Add subscription' : 'Edit subscription'}</h1>
          <p className="subtitle">Everything you need to decide at renewal time</p>
        </div>
      </header>

      <form className="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {errors.root && <p className="banner">{errors.root.message}</p>}

        <section className="card form-section">
          <h2>What is it?</h2>
          <div className="fields">
            <div className="field">
              <label htmlFor="name">Name</label>
              <input id="name" placeholder="e.g. Claude Pro" {...register('name')} />
              {fieldError('name')}
            </div>
            <div className="field">
              <label htmlFor="vendor">Vendor</label>
              <input id="vendor" placeholder="e.g. Anthropic" {...register('vendor')} />
              {fieldError('vendor')}
            </div>
            <div className="field">
              <label htmlFor="categoryId">Category</label>
              <select id="categoryId" {...register('categoryId')}>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
              {fieldError('categoryId')}
            </div>
            <div className="field">
              <span className="label" style={{ fontWeight: 600, fontSize: 13.5 }}>
                Type
              </span>
              <div className="segmented" role="radiogroup" aria-label="Type">
                <label>
                  <input type="radio" value="PAID" {...register('type')} /> Paid
                </label>
                <label>
                  <input type="radio" value="FREE_TRIAL" {...register('type')} /> Free trial
                </label>
              </div>
            </div>
          </div>
        </section>

        <section className="card form-section">
          <h2>Cost</h2>
          <div className="fields">
            {trial ? (
              <div className="field">
                <label htmlFor="priceAfterTrial">Price after the trial</label>
                <input id="priceAfterTrial" inputMode="decimal" {...register('priceAfterTrial')} />
                {fieldError('priceAfterTrial')}
              </div>
            ) : (
              <div className="field">
                <label htmlFor="amount">Price</label>
                <input id="amount" inputMode="decimal" placeholder="9.99" {...register('amount')} />
                {fieldError('amount')}
              </div>
            )}
            <div className="field">
              <label htmlFor="currency">Currency</label>
              <select id="currency" {...register('currency')}>
                {CURRENCIES.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="billingCycle">Billed</label>
              <select id="billingCycle" {...register('billingCycle')}>
                {BILLING_CYCLES.map((c) => (
                  <option key={c} value={c}>
                    {CYCLE_LABELS[c]}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="paymentLabel">Paid with (optional)</label>
              <input
                id="paymentLabel"
                placeholder="e.g. Visa ending 4417"
                {...register('paymentLabel')}
              />
              <span className="hint">A label only. Never enter a full card number.</span>
              {fieldError('paymentLabel')}
            </div>
          </div>
        </section>

        <section className="card form-section">
          <h2>Dates</h2>
          <div className="fields">
            <div className="field">
              <label htmlFor="startDate">Started</label>
              <input id="startDate" type="date" {...register('startDate')} />
              {fieldError('startDate')}
            </div>
            <div className="field">
              <label htmlFor="nextRenewalDate">{trial ? 'Trial ends' : 'Next renewal'}</label>
              <input id="nextRenewalDate" type="date" {...register('nextRenewalDate')} />
              {fieldError('nextRenewalDate')}
            </div>
            <div className="field">
              <label htmlFor="noticeDays">Notice period (days)</label>
              <input id="noticeDays" inputMode="numeric" {...register('noticeDays')} />
              {fieldError('noticeDays')}
            </div>
            <div className="field" style={{ alignSelf: 'end' }}>
              <label className="checkbox">
                <input type="checkbox" {...register('autoRenew')} /> Renews automatically
              </label>
            </div>
          </div>
        </section>

        <section className="card form-section">
          <h2>About it</h2>
          <div className="fields">
            <div className="field">
              <label htmlFor="owner">Who it's for</label>
              <input id="owner" {...register('owner')} />
              {fieldError('owner')}
            </div>
            <div className="field">
              <label htmlFor="usageRating">How often do you use it?</label>
              <select id="usageRating" {...register('usageRating')}>
                <option value="">Not rated</option>
                {USAGE_RATINGS.map((u) => (
                  <option key={u} value={u}>
                    {titleCase(u)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field full">
              <label htmlFor="notes">Notes</label>
              <textarea id="notes" rows={3} {...register('notes')} />
            </div>
          </div>
        </section>

        <div className="preview" aria-live="polite">
          <span>
            Per month: <strong>{monthly ? formatMoney({ amount: monthly, currency }) : '–'}</strong>
          </span>
          <span>
            Cancel by: <strong>{cancelBy ? formatDate(cancelBy) : '–'}</strong>
          </span>
        </div>

        <div className="form-actions">
          <Link to={id === undefined ? '/subscriptions' : `/subscriptions/${id}`} className="btn">
            Cancel
          </Link>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Saving…' : 'Save subscription'}
          </button>
        </div>
      </form>
    </>
  );
}

function cancelByPreview(renewal: string, noticeDays: string): string | null {
  if (!renewal || !/^\d{1,3}$/.test(noticeDays)) return null;
  const date = new Date(`${renewal}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - Number(noticeDays));
  return date.toISOString().slice(0, 10);
}
