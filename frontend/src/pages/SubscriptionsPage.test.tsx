import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderAt } from '../test/render';
import { SubscriptionsPage } from './SubscriptionsPage';

describe('SubscriptionsPage', () => {
  it('lists every subscription with its price in its own currency', async () => {
    renderAt('/subscriptions', '/subscriptions', <SubscriptionsPage />);

    expect(await screen.findByText('ChatGPT Plus')).toBeInTheDocument();
    expect(screen.getByText('$20.00')).toBeInTheDocument();
    expect(screen.getByText('₹1,499.00')).toBeInTheDocument();
  });

  it('filters by currency', async () => {
    const user = userEvent.setup();
    renderAt('/subscriptions', '/subscriptions', <SubscriptionsPage />);
    await screen.findByText('ChatGPT Plus');

    await user.selectOptions(screen.getByLabelText('Currency'), 'INR');

    expect(screen.getByText('Mobile plan')).toBeInTheDocument();
    expect(screen.queryByText('ChatGPT Plus')).not.toBeInTheDocument();
  });

  it('marks a free trial with the price that follows', async () => {
    renderAt('/subscriptions', '/subscriptions', <SubscriptionsPage />);

    expect(await screen.findByText('then £29.99')).toBeInTheDocument();
    expect(screen.getByText('Free trial')).toBeInTheDocument();
  });
});
