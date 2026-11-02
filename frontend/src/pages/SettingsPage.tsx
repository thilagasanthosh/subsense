import { useState } from 'react';
import { useNotificationSettings, useSendTestNotification } from '../api/hooks';

export function SettingsPage() {
  const { data, isLoading, error } = useNotificationSettings();
  const sendTest = useSendTestNotification();
  const [testResult, setTestResult] = useState<'idle' | 'sent' | 'error'>('idle');
  const [testError, setTestError] = useState<string>('');

  async function handleSendTest() {
    setTestResult('idle');
    setTestError('');
    try {
      await sendTest.mutateAsync();
      setTestResult('sent');
    } catch (err) {
      setTestResult('error');
      setTestError(err instanceof Error ? err.message : 'Unknown error');
    }
  }

  return (
    <div className="page">
      <h1>Settings</h1>

      <section className="card" aria-labelledby="notifications-heading">
        <h2 id="notifications-heading">Notifications</h2>

        {isLoading && <p>Loading…</p>}
        {error && <p className="error">Failed to load notification settings.</p>}

        {data && (
          <>
            <dl className="detail-list">
              <dt>ntfy server</dt>
              <dd>{data.server}</dd>

              <dt>Topic</dt>
              <dd>
                {data.topicConfigured ? (
                  <span className="badge badge--ok">Topic configured</span>
                ) : (
                  <span className="badge badge--warn">Topic not configured</span>
                )}
              </dd>

              <dt>Reminder days before renewal</dt>
              <dd>{data.offsets.join(', ')}</dd>
            </dl>

            {data.topicConfigured && (
              <p className="hint">
                Subscribe to your topic in the <strong>ntfy app</strong> to receive reminders on
                your phone.
              </p>
            )}

            {!data.topicConfigured && (
              <p className="hint">
                Set <code>SUBSENSE_NTFY_TOPIC</code> in your <code>.env</code> file to enable push
                reminders.
              </p>
            )}

            <div className="actions">
              <button
                type="button"
                className="btn"
                disabled={!data.topicConfigured || sendTest.isPending}
                onClick={handleSendTest}
                aria-disabled={!data.topicConfigured}
              >
                Send test notification
              </button>

              {testResult === 'sent' && (
                <span role="status" className="feedback feedback--ok">
                  Sent. Check your phone.
                </span>
              )}
              {testResult === 'error' && (
                <span role="alert" className="feedback feedback--error">
                  {testError}
                </span>
              )}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
