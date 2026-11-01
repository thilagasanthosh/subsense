import type { Decision, SubscriptionStatus } from '../api/types';
import { STATUS_LABELS, renewsIn, titleCase, urgencyOf } from '../lib/format';

export function RenewalBadge({ days }: { days: number }) {
  return <span className={`badge badge-${urgencyOf(days)}`}>{renewsIn(days)}</span>;
}

export function StatusBadge({ status }: { status: SubscriptionStatus }) {
  const tone =
    status === 'CANCELLING'
      ? 'badge-urgent'
      : status === 'TRIAL'
        ? 'badge-accent'
        : 'badge-neutral';
  return <span className={`badge ${tone}`}>{STATUS_LABELS[status]}</span>;
}

export function DecisionBadge({ decision }: { decision: Decision }) {
  if (decision === 'PENDING') {
    return <span className="badge badge-neutral">Undecided</span>;
  }
  return <span className="badge badge-accent">{titleCase(decision)}</span>;
}

export function CategoryLabel({ name, colour }: { name: string; colour: string }) {
  return (
    <span className="checkbox" style={{ fontWeight: 500 }}>
      <span className="dot" style={{ background: colour }} />
      {name}
    </span>
  );
}
