import { Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { DashboardPage } from './pages/DashboardPage';
import { RenewalsPage } from './pages/RenewalsPage';
import { SettingsPage } from './pages/SettingsPage';
import { SubscriptionDetailPage } from './pages/SubscriptionDetailPage';
import { SubscriptionFormPage } from './pages/SubscriptionFormPage';
import { SubscriptionsPage } from './pages/SubscriptionsPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="subscriptions" element={<SubscriptionsPage />} />
        <Route path="subscriptions/new" element={<SubscriptionFormPage />} />
        <Route path="subscriptions/:id" element={<SubscriptionDetailPage />} />
        <Route path="subscriptions/:id/edit" element={<SubscriptionFormPage />} />
        <Route path="renewals" element={<RenewalsPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<p className="empty">Page not found.</p>} />
      </Route>
    </Routes>
  );
}
