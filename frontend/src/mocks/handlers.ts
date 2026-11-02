import { http, HttpResponse } from 'msw';
import type { DecisionRequest, SubscriptionDetails } from '../api/types';
import { categories, db } from './db';

const base = '/api/v1';

const notFound = () =>
  HttpResponse.json({ title: 'Not Found', detail: 'Subscription not found' }, { status: 404 });

const cancelled = (id: number) =>
  db.get(id)?.status === 'CANCELLED'
    ? HttpResponse.json({ title: 'Conflict', detail: 'Subscription is cancelled' }, { status: 409 })
    : null;

/** Mirrors the backend's cross-field rules so validation errors look the same. */
function validate(details: SubscriptionDetails) {
  if (details.nextRenewalDate < details.startDate) {
    return HttpResponse.json(
      {
        title: 'Validation failed',
        detail: 'Some fields are not valid.',
        errors: { nextRenewalDate: 'must be on or after the start date' },
      },
      { status: 400 },
    );
  }
  return null;
}

export const handlers = [
  http.get(`${base}/categories`, () => HttpResponse.json(categories)),

  http.get(`${base}/dashboard/summary`, () => HttpResponse.json(db.dashboard())),

  http.get(`${base}/subscriptions`, () => HttpResponse.json(db.list())),

  http.get(`${base}/subscriptions/:id`, ({ params }) => {
    const s = db.get(Number(params.id));
    return s ? HttpResponse.json(s) : notFound();
  }),

  http.post(`${base}/subscriptions`, async ({ request }) => {
    const details = (await request.json()) as SubscriptionDetails;
    return validate(details) ?? HttpResponse.json(db.save(details), { status: 201 });
  }),

  http.put(`${base}/subscriptions/:id`, async ({ params, request }) => {
    const details = (await request.json()) as SubscriptionDetails;
    if (!db.get(Number(params.id))) return notFound();
    return (
      cancelled(Number(params.id)) ??
      validate(details) ??
      HttpResponse.json(db.save(details, Number(params.id)))
    );
  }),

  http.delete(`${base}/subscriptions/:id`, ({ params }) => {
    db.remove(Number(params.id));
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${base}/subscriptions/:id/decisions`, async ({ params, request }) => {
    const conflict = cancelled(Number(params.id));
    if (conflict) return conflict;
    const s = db.decide(Number(params.id), (await request.json()) as DecisionRequest);
    return s ? HttpResponse.json(s) : notFound();
  }),

  http.get(`${base}/subscriptions/:id/decisions`, ({ params }) =>
    HttpResponse.json(db.decisions(Number(params.id))),
  ),

  http.get(`${base}/settings/notifications`, () =>
    HttpResponse.json({
      server: 'https://ntfy.sh',
      topicConfigured: false,
      offsets: [14, 7, 1],
    }),
  ),

  http.post(`${base}/settings/notifications/test`, () =>
    HttpResponse.json({ detail: 'Notifications are not configured' }, { status: 503 }),
  ),
];
