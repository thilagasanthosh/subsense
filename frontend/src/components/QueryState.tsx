import type { ReactNode } from 'react';

interface Props {
  isLoading: boolean;
  error: Error | null;
  children: ReactNode;
}

/** Shows loading and error states consistently, then the content. */
export function QueryState({ isLoading, error, children }: Props) {
  if (isLoading) {
    return <p className="empty">Loading…</p>;
  }
  if (error) {
    return (
      <p className="banner" role="alert">
        Could not load data: {error.message}
      </p>
    );
  }
  return <>{children}</>;
}
