import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { describe, it, expect, afterAll, afterEach, beforeAll } from 'vitest';
import { SettingsPage } from './SettingsPage';

const base = '/api/v1';

function configured() {
  return {
    server: 'https://ntfy.sh',
    topicConfigured: true,
    offsets: [14, 7, 1],
  };
}

function unconfigured() {
  return {
    server: 'https://ntfy.sh',
    topicConfigured: false,
    offsets: [14, 7, 1],
  };
}

const server = setupServer(
  http.get(`${base}/settings/notifications`, () => HttpResponse.json(configured())),
  http.post(`${base}/settings/notifications/test`, () => new HttpResponse(null, { status: 204 })),
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

// Test 47: SettingsPage shows server and topic-configured status
describe('SettingsPage', () => {
  it('shows the ntfy server and topic-configured badge', async () => {
    renderPage();
    await screen.findByText('https://ntfy.sh');
    expect(screen.getByText(/topic configured/i)).toBeInTheDocument();
    expect(screen.queryByText(/topic not configured/i)).not.toBeInTheDocument();
  });

  it('shows "Topic not configured" when topic is absent', async () => {
    server.use(http.get(`${base}/settings/notifications`, () => HttpResponse.json(unconfigured())));
    renderPage();
    await screen.findByText(/topic not configured/i);
    expect(screen.queryByText(/topic configured/i)).not.toBeInTheDocument();
  });

  it('shows the reminder offsets', async () => {
    renderPage();
    await screen.findByText(/14/);
    expect(screen.getByText(/7/)).toBeInTheDocument();
    expect(screen.getByText(/1/)).toBeInTheDocument();
  });

  // Test 48: "Send test notification" shows success message on click
  it('shows success message after sending a test notification', async () => {
    renderPage();
    const button = await screen.findByRole('button', { name: /send test/i });
    await userEvent.click(button);
    await screen.findByText(/sent.*check your phone/i);
  });

  // Test 49: shows error message on failure
  it('shows error message when test notification fails', async () => {
    server.use(
      http.post(`${base}/settings/notifications/test`, () =>
        HttpResponse.json({ detail: 'ntfy did not accept the notification' }, { status: 502 }),
      ),
    );
    renderPage();
    const button = await screen.findByRole('button', { name: /send test/i });
    await userEvent.click(button);
    await screen.findByText(/ntfy did not accept/i);
  });

  // Test 50: navigation has a Settings link (rendered in isolation via heading)
  it('has a Settings heading', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /settings/i })).toBeInTheDocument(),
    );
  });
});
