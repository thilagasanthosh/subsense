import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { db } from '../mocks/db';
import { renderAt } from '../test/render';
import { SubscriptionFormPage } from './SubscriptionFormPage';

describe('SubscriptionFormPage', () => {
  it('shows what is missing instead of saving', async () => {
    const user = userEvent.setup();
    renderAt('/subscriptions/new', '/subscriptions/new', <SubscriptionFormPage />);

    await user.click(await screen.findByRole('button', { name: 'Save subscription' }));

    expect(await screen.findByText('Name is required')).toBeInTheDocument();
    expect(screen.getByText('Vendor is required')).toBeInTheDocument();
  });

  it('previews the monthly cost and saves a yearly rupee subscription', async () => {
    const user = userEvent.setup();
    renderAt('/subscriptions/new', '/subscriptions/new', <SubscriptionFormPage />);

    await user.type(await screen.findByLabelText('Name'), 'Music India');
    await user.type(screen.getByLabelText('Vendor'), 'Example Music');
    await user.type(screen.getByLabelText('Price'), '1200');
    await user.selectOptions(screen.getByLabelText('Currency'), 'INR');
    await user.selectOptions(screen.getByLabelText('Billed'), 'YEARLY');
    await user.type(screen.getByLabelText('Next renewal'), '2027-03-01');

    expect(screen.getByText('₹100.00')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Save subscription' }));

    expect(await screen.findByText('navigated away')).toBeInTheDocument();
    expect(db.list().map((s) => s.name)).toContain('Music India');
  });

  it('asks for the price after the trial for a free trial', async () => {
    const user = userEvent.setup();
    renderAt('/subscriptions/new', '/subscriptions/new', <SubscriptionFormPage />);

    await user.click(await screen.findByLabelText('Free trial'));

    expect(screen.getByLabelText('Price after the trial')).toBeInTheDocument();
    expect(screen.getByLabelText('Trial ends')).toBeInTheDocument();
  });
});
