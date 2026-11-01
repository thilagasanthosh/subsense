import { Link } from 'react-router-dom';
import { useSubscriptions } from '../api/hooks';
import type { Subscription } from '../api/types';
import { DecisionBadge, RenewalBadge } from '../components/Badges';
import { QueryState } from '../components/QueryState';
import { formatDate, formatMoney } from '../lib/format';

const WINDOW_DAYS = 90;

function weekLabel(weekIndex: number): string {
  if (weekIndex === 0) return 'This week';
  if (weekIndex === 1) return 'Next week';
  return `In ${weekIndex} weeks`;
}

function groupByWeek(subscriptions: Subscription[]): [number, Subscription[]][] {
  const groups = new Map<number, Subscription[]>();
  for (const s of subscriptions) {
    const week = Math.floor(s.daysUntilRenewal / 7);
    groups.set(week, [...(groups.get(week) ?? []), s]);
  }
  return [...groups.entries()].sort(([a], [b]) => a - b);
}

export function RenewalsPage() {
  const { data = [], isLoading, error } = useSubscriptions();
  const upcoming = data.filter(
    (s) =>
      s.status !== 'CANCELLING' && s.daysUntilRenewal >= 0 && s.daysUntilRenewal <= WINDOW_DAYS,
  );

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Renewals</h1>
          <p className="subtitle">Renewals and trial endings in the next {WINDOW_DAYS} days</p>
        </div>
      </header>

      <QueryState isLoading={isLoading} error={error}>
        {upcoming.length === 0 ? (
          <p className="card empty">Nothing renews in the next {WINDOW_DAYS} days.</p>
        ) : (
          groupByWeek(upcoming).map(([week, items]) => (
            <section key={week} className="timeline-week">
              <h2>{weekLabel(week)}</h2>
              <div className="card" style={{ padding: '4px 20px' }}>
                <ul className="decision-list">
                  {items.map((s) => (
                    <li key={s.id}>
                      <div>
                        <Link to={`/subscriptions/${s.id}`} className="cell-title">
                          {s.name}
                        </Link>
                        <div className="cell-sub">
                          {s.type === 'FREE_TRIAL' ? 'Trial ends' : 'Renews'}{' '}
                          {formatDate(s.nextRenewalDate)} ·{' '}
                          {formatMoney(s.type === 'FREE_TRIAL' ? s.priceAfterTrial : s.price)}
                        </div>
                      </div>
                      <div className="actions">
                        <DecisionBadge decision={s.decision} />
                        <RenewalBadge days={s.daysUntilRenewal} />
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            </section>
          ))
        )}
      </QueryState>
    </>
  );
}
