import { screen } from '@testing-library/react';
import { renderAt } from '../test/render';
import { RenewalsPage } from './RenewalsPage';

describe('RenewalsPage', () => {
  it('leaves out subscriptions that are already cancelled', async () => {
    renderAt('/renewals', '/renewals', <RenewalsPage />);

    expect(await screen.findByText('Claude Pro')).toBeInTheDocument();
    expect(screen.queryByText('Disney+')).not.toBeInTheDocument();
  });
});
