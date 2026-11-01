import { Link } from 'react-router-dom';
import { useDashboard } from '../api/hooks';
import { RenewalBadge } from '../components/Badges';
import { CategoryDonut } from '../components/CategoryDonut';
import { DecisionButtons } from '../components/DecisionButtons';
import { QueryState } from '../components/QueryState';
import { formatDate, formatMoney } from '../lib/format';

export function DashboardPage() {
  const { data, isLoading, error } = useDashboard();

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="subtitle">Your recurring spend at a glance</p>
        </div>
        <Link to="/subscriptions/new" className="btn btn-primary">
          Add subscription
        </Link>
      </header>

      <QueryState isLoading={isLoading} error={error}>
        {data && (
          <>
            <section className="stats" aria-label="Summary">
              <div className="card">
                <div className="stat-label">Monthly spend</div>
                <div className="stat-value" data-testid="monthly-spend">
                  {formatMoney(data.monthlySpend)}
                </div>
                <div className="stat-hint">All currencies in {data.reportingCurrency}</div>
              </div>
              <div className="card">
                <div className="stat-label">Projected yearly</div>
                <div className="stat-value">{formatMoney(data.annualSpend)}</div>
                <div className="stat-hint">If nothing changes</div>
              </div>
              <div className="card">
                <div className="stat-label">Active subscriptions</div>
                <div className="stat-value">{data.activeCount}</div>
                <div className="stat-hint">Including free trials</div>
              </div>
              <div className="card">
                <div className="stat-label">Renewing in 30 days</div>
                <div className="stat-value">{data.renewalsNext30Days}</div>
                <div className="stat-hint">
                  <Link to="/renewals">See the timeline</Link>
                </div>
              </div>
            </section>

            <div className="grid-2">
              <section className="card" aria-labelledby="by-category">
                <h2 id="by-category">Monthly spend by category</h2>
                <CategoryDonut data={data.spendByCategory} />
              </section>

              <section className="card" aria-labelledby="needs-decision">
                <h2 id="needs-decision">Needs a decision</h2>
                <p className="hint" style={{ marginTop: -8 }}>
                  Renewing in the next 14 days with no decision yet
                </p>
                {data.needsDecision.length === 0 ? (
                  <p className="empty">Nothing to decide right now.</p>
                ) : (
                  <ul className="decision-list">
                    {data.needsDecision.map((s) => (
                      <li key={s.id}>
                        <div>
                          <Link to={`/subscriptions/${s.id}`} className="cell-title">
                            {s.name}
                          </Link>
                          <div className="cell-sub">
                            {s.type === 'FREE_TRIAL'
                              ? `Free trial, then ${formatMoney(s.priceAfterTrial)}`
                              : formatMoney(s.price)}{' '}
                            · cancel by {formatDate(s.cancelByDate)}
                          </div>
                        </div>
                        <RenewalBadge days={s.daysUntilRenewal} />
                        <DecisionButtons subscriptionId={s.id} name={s.name} current={s.decision} />
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>

            <p className="hint" style={{ marginTop: 14 }}>
              Exchange rates as of {formatDate(data.fxRatesAsOf)} (European Central Bank)
            </p>
          </>
        )}
      </QueryState>
    </>
  );
}
