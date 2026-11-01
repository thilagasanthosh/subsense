import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSubscriptions } from '../api/hooks';
import { CURRENCIES, type CurrencyCode, type Subscription } from '../api/types';
import { CategoryLabel, RenewalBadge, StatusBadge } from '../components/Badges';
import { QueryState } from '../components/QueryState';
import { CYCLE_LABELS, formatMoney } from '../lib/format';

interface Filters {
  search: string;
  category: string;
  currency: '' | CurrencyCode;
}

function matches(s: Subscription, f: Filters): boolean {
  const text = `${s.name} ${s.vendor}`.toLowerCase();
  return (
    text.includes(f.search.trim().toLowerCase()) &&
    (!f.category || s.category.name === f.category) &&
    (!f.currency || s.price.currency === f.currency)
  );
}

export function SubscriptionsPage() {
  const { data = [], isLoading, error } = useSubscriptions();
  const [filters, setFilters] = useState<Filters>({ search: '', category: '', currency: '' });

  const categories = useMemo(() => [...new Set(data.map((s) => s.category.name))].sort(), [data]);
  const visible = data.filter((s) => matches(s, filters));

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Subscriptions</h1>
          <p className="subtitle">{data.length} in your register, soonest renewal first</p>
        </div>
        <Link to="/subscriptions/new" className="btn btn-primary">
          Add subscription
        </Link>
      </header>

      <div className="toolbar">
        <input
          type="search"
          placeholder="Search name or vendor"
          aria-label="Search"
          style={{ maxWidth: 260 }}
          value={filters.search}
          onChange={(e) => setFilters({ ...filters, search: e.target.value })}
        />
        <select
          aria-label="Category"
          style={{ maxWidth: 190 }}
          value={filters.category}
          onChange={(e) => setFilters({ ...filters, category: e.target.value })}
        >
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
        <select
          aria-label="Currency"
          style={{ maxWidth: 150 }}
          value={filters.currency}
          onChange={(e) =>
            setFilters({ ...filters, currency: e.target.value as Filters['currency'] })
          }
        >
          <option value="">All currencies</option>
          {CURRENCIES.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
      </div>

      <QueryState isLoading={isLoading} error={error}>
        <section className="card" style={{ padding: 6 }}>
          {visible.length === 0 ? (
            <p className="empty">
              {data.length === 0 ? 'No subscriptions yet.' : 'Nothing matches these filters.'}
            </p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Subscription</th>
                    <th>Category</th>
                    <th className="num">Price</th>
                    <th>Cycle</th>
                    <th className="num">Per month</th>
                    <th>Renews</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map((s) => (
                    <tr key={s.id}>
                      <td>
                        <Link to={`/subscriptions/${s.id}`} className="cell-title">
                          {s.name}
                        </Link>
                        <div className="cell-sub">{s.vendor}</div>
                      </td>
                      <td>
                        <CategoryLabel name={s.category.name} colour={s.category.colour} />
                      </td>
                      <td className="num">
                        {s.type === 'FREE_TRIAL' ? (
                          <>
                            <div>Free</div>
                            <div className="cell-sub">then {formatMoney(s.priceAfterTrial)}</div>
                          </>
                        ) : (
                          formatMoney(s.price)
                        )}
                      </td>
                      <td>{CYCLE_LABELS[s.billingCycle]}</td>
                      <td className="num">{formatMoney(s.monthlyEquivalentReporting)}</td>
                      <td>
                        <RenewalBadge days={s.daysUntilRenewal} />
                      </td>
                      <td>
                        <StatusBadge status={s.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </QueryState>
    </>
  );
}
