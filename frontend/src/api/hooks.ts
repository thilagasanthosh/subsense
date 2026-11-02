import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type {
  Category,
  DashboardSummary,
  DecisionEntry,
  DecisionRequest,
  NotificationSettings,
  Subscription,
  SubscriptionDetails,
} from './types';

const keys = {
  subscriptions: ['subscriptions'] as const,
  subscription: (id: number) => ['subscriptions', id] as const,
  decisions: (id: number) => ['subscriptions', id, 'decisions'] as const,
  dashboard: ['dashboard'] as const,
  categories: ['categories'] as const,
  notificationSettings: ['notificationSettings'] as const,
};

export const useSubscriptions = () =>
  useQuery({ queryKey: keys.subscriptions, queryFn: () => api<Subscription[]>('/subscriptions') });

export const useSubscription = (id: number) =>
  useQuery({
    queryKey: keys.subscription(id),
    queryFn: () => api<Subscription>(`/subscriptions/${id}`),
  });

export const useDecisions = (id: number) =>
  useQuery({
    queryKey: keys.decisions(id),
    queryFn: () => api<DecisionEntry[]>(`/subscriptions/${id}/decisions`),
  });

export const useDashboard = () =>
  useQuery({
    queryKey: keys.dashboard,
    queryFn: () => api<DashboardSummary>('/dashboard/summary'),
  });

export const useCategories = () =>
  useQuery({
    queryKey: keys.categories,
    queryFn: () => api<Category[]>('/categories'),
    staleTime: Infinity,
  });

/** Every write changes totals and lists, so refresh all subscription data afterwards. */
function useInvalidateAll() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.subscriptions }),
      queryClient.invalidateQueries({ queryKey: keys.dashboard }),
    ]);
}

export function useSaveSubscription(id?: number) {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (details: SubscriptionDetails) =>
      id === undefined
        ? api<Subscription>('/subscriptions', { method: 'POST', body: JSON.stringify(details) })
        : api<Subscription>(`/subscriptions/${id}`, {
            method: 'PUT',
            body: JSON.stringify(details),
          }),
    onSuccess: invalidate,
  });
}

export function useDeleteSubscription() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/subscriptions/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });
}

export function useDecide(id: number) {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (request: DecisionRequest) =>
      api<Subscription>(`/subscriptions/${id}/decisions`, {
        method: 'POST',
        body: JSON.stringify(request),
      }),
    onSuccess: invalidate,
  });
}

export const useNotificationSettings = () =>
  useQuery({
    queryKey: keys.notificationSettings,
    queryFn: () => api<NotificationSettings>('/settings/notifications'),
  });

export const useSendTestNotification = () =>
  useMutation({
    mutationFn: () => api<void>('/settings/notifications/test', { method: 'POST' }),
  });
