import { db } from './db';

// The mock API must answer like the backend, so `npm run dev:mock` shows the same errors.
describe('mock API', () => {
  const cancelledId = () => db.list().find((s) => s.status === 'CANCELLED')!.id;

  it('rejects a decision on a cancelled subscription with 409', async () => {
    const response = await fetch(`/api/v1/subscriptions/${cancelledId()}/decisions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'RENEW' }),
    });

    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({ detail: 'Subscription is cancelled' });
  });

  it('rejects an edit of a cancelled subscription with 409', async () => {
    const id = cancelledId();
    const response = await fetch(`/api/v1/subscriptions/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...db.get(id), name: 'Renamed' }),
    });

    expect(response.status).toBe(409);
  });
});
