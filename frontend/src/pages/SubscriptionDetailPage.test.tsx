import { screen } from '@testing-library/react';
import { db } from '../mocks/db';
import { renderAt } from '../test/render';
import { SubscriptionDetailPage } from './SubscriptionDetailPage';

describe('SubscriptionDetailPage', () => {
  const cancelled = () => db.list().find((s) => s.name === 'Disney+')!;

  it('shows when a cancelled subscription ended and offers no more decisions', async () => {
    const id = cancelled().id;
    renderAt(`/subscriptions/${id}`, '/subscriptions/:id', <SubscriptionDetailPage />);

    expect(await screen.findByRole('heading', { name: 'Disney+' })).toBeInTheDocument();
    expect(screen.getByText(/Cancelled on \d+ \w+ \d{4}/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Renew/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Downgrade to')).not.toBeInTheDocument();
  });

  it('does not show a next renewal for a cancelled subscription', async () => {
    const id = cancelled().id;
    renderAt(`/subscriptions/${id}`, '/subscriptions/:id', <SubscriptionDetailPage />);

    expect(await screen.findByText('Ended')).toBeInTheDocument();
    expect(screen.queryByText('Next renewal')).not.toBeInTheDocument();
    expect(screen.queryByText('Today')).not.toBeInTheDocument();
  });

  it('still offers decisions for a live subscription', async () => {
    const id = db.list().find((s) => s.name === 'Claude Pro')!.id;
    renderAt(`/subscriptions/${id}`, '/subscriptions/:id', <SubscriptionDetailPage />);

    expect(await screen.findByRole('link', { name: 'Edit' })).toBeInTheDocument();
    expect(screen.getByLabelText('Downgrade to')).toBeInTheDocument();
  });
});
