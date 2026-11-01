import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderAt } from '../test/render';
import { DashboardPage } from './DashboardPage';

describe('DashboardPage', () => {
  it('shows total monthly spend in the reporting currency', async () => {
    renderAt('/', '/', <DashboardPage />);

    expect(await screen.findByTestId('monthly-spend')).toHaveTextContent(/^£\d+\.\d{2}$/);
    expect(screen.getByText('All currencies in GBP')).toBeInTheDocument();
  });

  it('lists renewals needing a decision and removes one once cancelled', async () => {
    const user = userEvent.setup();
    renderAt('/', '/', <DashboardPage />);

    const list = within(await screen.findByRole('region', { name: 'Needs a decision' }));
    expect(list.getByText('Claude Pro')).toBeInTheDocument();
    // Already marked for cancellation, so not waiting on a decision
    expect(list.queryByText('Antivirus Plus')).not.toBeInTheDocument();

    await user.click(list.getByRole('button', { name: 'Cancel Claude Pro' }));

    await waitFor(() => expect(list.queryByText('Claude Pro')).not.toBeInTheDocument());
  });
});
