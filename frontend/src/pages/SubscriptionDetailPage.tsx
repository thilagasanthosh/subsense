import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useDecide, useDecisions, useDeleteSubscription, useSubscription } from '../api/hooks';
import type { Subscription } from '../api/types';
import { CategoryLabel, DecisionBadge, RenewalBadge, StatusBadge } from '../components/Badges';
import { DecisionButtons } from '../components/DecisionButtons';
import { QueryState } from '../components/QueryState';
import { CYCLE_LABELS, formatDate, formatMoney, titleCase } from '../lib/format';

export function SubscriptionDetailPage() {
  const id = Number(useParams().id);
  const { data, isLoading, error } = useSubscription(id);

  return (
    <QueryState isLoading={isLoading} error={error}>
      {data && <Detail subscription={data} />}
    </QueryState>
  );
}

function Detail({ subscription: s }: { subscription: Subscription }) {
  const navigate = useNavigate();
  const remove = useDeleteSubscription();

  const onDelete = () => {
    if (window.confirm(`Delete ${s.name}? It stays in your history but leaves the register.`)) {
      remove.mutate(s.id, { onSuccess: () => navigate('/subscriptions') });
    }
  };

  return (
    <>
      <header className="page-header">
        <div>
          <h1>{s.name}</h1>
          <p className="subtitle">
            {s.vendor} · <StatusBadge status={s.status} />
          </p>
        </div>
        <div className="actions">
          <Link to={`/subscriptions/${s.id}/edit`} className="btn">
            Edit
          </Link>
          <button type="button" className="btn btn-danger" onClick={onDelete}>
            Delete
          </button>
        </div>
      </header>

      <div className="stack">
        <section className="card">
          <dl className="detail-grid">
            <div>
              <dt>Price</dt>
              <dd>
                {s.type === 'FREE_TRIAL'
                  ? `Free, then ${formatMoney(s.priceAfterTrial)}`
                  : formatMoney(s.price)}{' '}
                {CYCLE_LABELS[s.billingCycle].toLowerCase()}
              </dd>
            </div>
            <div>
              <dt>Per month</dt>
              <dd>
                {formatMoney(s.monthlyEquivalent)}
                {s.monthlyEquivalent.currency !== s.monthlyEquivalentReporting.currency &&
                  ` (${formatMoney(s.monthlyEquivalentReporting)})`}
              </dd>
            </div>
            <div>
              <dt>Per year</dt>
              <dd>{formatMoney(s.annualCostReporting)}</dd>
            </div>
            <div>
              <dt>{s.type === 'FREE_TRIAL' ? 'Trial ends' : 'Next renewal'}</dt>
              <dd>
                {formatDate(s.nextRenewalDate)} <RenewalBadge days={s.daysUntilRenewal} />
              </dd>
            </div>
            <div>
              <dt>Cancel by</dt>
              <dd>
                {formatDate(s.cancelByDate)}
                {s.noticeDays > 0 && ` (${s.noticeDays} days' notice)`}
              </dd>
            </div>
            <div>
              <dt>Category</dt>
              <dd>
                <CategoryLabel name={s.category.name} colour={s.category.colour} />
              </dd>
            </div>
            <div>
              <dt>Paid with</dt>
              <dd>{s.paymentLabel ?? '–'}</dd>
            </div>
            <div>
              <dt>For</dt>
              <dd>{s.owner}</dd>
            </div>
            <div>
              <dt>Usage</dt>
              <dd>{s.usageRating ? titleCase(s.usageRating) : 'Not rated'}</dd>
            </div>
          </dl>
          {s.notes && (
            <p className="hint" style={{ marginTop: 16 }}>
              {s.notes}
            </p>
          )}
        </section>

        <DecisionPanel subscription={s} />
        <DecisionHistory id={s.id} />
      </div>
    </>
  );
}

function DecisionPanel({ subscription: s }: { subscription: Subscription }) {
  const decide = useDecide(s.id);
  const [newAmount, setNewAmount] = useState('');

  return (
    <section className="card" aria-labelledby="decision">
      <h2 id="decision">Next renewal</h2>
      <ul className="decision-list">
        <li>
          <div>
            Current decision: <DecisionBadge decision={s.decision} />
            {s.downgradeAmount && (
              <span className="cell-sub"> to {formatMoney(s.downgradeAmount)}</span>
            )}
          </div>
          <DecisionButtons subscriptionId={s.id} name={s.name} current={s.decision} />
        </li>
      </ul>
      <form
        className="toolbar"
        style={{ marginTop: 8, marginBottom: 0, alignItems: 'center' }}
        onSubmit={(e) => {
          e.preventDefault();
          decide.mutate(
            { decision: 'DOWNGRADE', newAmount },
            { onSuccess: () => setNewAmount('') },
          );
        }}
      >
        <label htmlFor="downgrade">Downgrade to</label>
        <input
          id="downgrade"
          inputMode="decimal"
          placeholder={`New price in ${s.price.currency}`}
          style={{ maxWidth: 200 }}
          value={newAmount}
          onChange={(e) => setNewAmount(e.target.value)}
        />
        <button className="btn btn-sm" disabled={!/^\d+(\.\d{1,2})?$/.test(newAmount)}>
          Save downgrade
        </button>
      </form>
    </section>
  );
}

function DecisionHistory({ id }: { id: number }) {
  const { data = [] } = useDecisions(id);
  if (data.length === 0) return null;
  return (
    <section className="card" aria-labelledby="history">
      <h2 id="history">Decision history</h2>
      <ul className="decision-list">
        {data.map((d) => (
          <li key={d.decidedAt}>
            <div>
              <DecisionBadge decision={d.decision} />
              {d.reason && <span className="cell-sub"> {d.reason}</span>}
            </div>
            <span className="cell-sub">
              {new Date(d.decidedAt).toLocaleString('en-GB')} · via {d.source}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
