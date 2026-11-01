import { useDecide } from '../api/hooks';
import type { Decision } from '../api/types';

interface Props {
  subscriptionId: number;
  name: string;
  current: Decision;
}

/** Renew / Cancel for the next renewal. Downgrade lives on the detail page, where a price is entered. */
export function DecisionButtons({ subscriptionId, name, current }: Props) {
  const decide = useDecide(subscriptionId);
  const choose = (decision: Decision) => decide.mutate({ decision });

  return (
    <div className="actions">
      <button
        type="button"
        className="btn btn-sm"
        aria-pressed={current === 'RENEW'}
        aria-label={`Renew ${name}`}
        disabled={decide.isPending}
        onClick={() => choose('RENEW')}
      >
        Renew
      </button>
      <button
        type="button"
        className="btn btn-sm btn-danger"
        aria-pressed={current === 'CANCEL'}
        aria-label={`Cancel ${name}`}
        disabled={decide.isPending}
        onClick={() => choose('CANCEL')}
      >
        Cancel
      </button>
    </div>
  );
}
