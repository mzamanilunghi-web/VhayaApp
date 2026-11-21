import { lazy, Suspense, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ErrorBoundary } from '@/components/error-boundary';
import { Toaster } from '@/components/ui/toaster';
import { TooltipProvider } from '@/components/ui/tooltip';
import DriverReview from '@/pages/driver-review';
import AccountDeletions from '@/pages/account-deletions';
import { AuthProvider } from '@/lib/auth-context';
import { useAuth } from '@/lib/auth-context';
import NotFound from '@/pages/not-found';
import {
  Route,
  Switch,
  useLocation,
  Router as WouterRouter,
} from 'wouter';

const queryClient = new QueryClient();
const OverviewPage = lazy(() => import('@/pages/admin-workspace').then((module) => ({ default: module.OverviewPage })));
const RidesPage = lazy(() => import('@/pages/admin-workspace').then((module) => ({ default: module.RidesPage })));
const PeoplePage = lazy(() => import('@/pages/admin-workspace').then((module) => ({ default: module.PeoplePage })));
const CompliancePage = lazy(() => import('@/pages/admin-workspace').then((module) => ({ default: module.CompliancePage })));
const SupportPage = lazy(() => import('@/pages/admin-workspace').then((module) => ({ default: module.SupportPage })));
const FinancePage = lazy(() => import('@/pages/admin-workspace').then((module) => ({ default: module.FinancePage })));
const SubscriptionsPage = lazy(() => import('@/pages/subscriptions'));
const DriversMapPage = lazy(() => import('@/pages/drivers-map'));

function Router() {
  return (
    // Keep a shared shell (sidebar, navbar) outside the boundary so it
    // survives a page crash.
    <RoutedErrorBoundary>
      <Switch>
        <Route path="/" component={OverviewRoute} />
        <Route path="/rides" component={RidesRoute} />
        <Route path="/drivers-map" component={DriversMapRoute} />
        <Route path="/people" component={PeopleRoute} />
        <Route path="/driver-reviews" component={DriverReview} />
        <Route path="/driver-compliance" component={ComplianceRoute} />
        <Route path="/support" component={SupportRoute} />
        <Route path="/finance" component={FinanceRoute} />
        <Route path="/subscriptions" component={SubscriptionsRoute} />
        <Route path="/account-deletions" component={AccountDeletions} />
        <Route component={NotFound} />
      </Switch>
    </RoutedErrorBoundary>
  );
}

function AdminOnly({ children }: { children: ReactNode }) {
  const auth = useAuth();
  return auth.status === 'ready'
    ? <Suspense fallback={<WorkspaceLoading />}>{children}</Suspense>
    : <DriverReview />;
}

function WorkspaceLoading() {
  return <div className="auth-screen" role="status" data-testid="loading-admin-workspace">
    <section className="auth-card"><div className="brand"><span className="brand-mark">V</span><span><span className="brand-name">vhaya</span><span className="brand-sub">operations</span></span></div>
      <div className="auth-skeleton" /><p>Loading admin workspace…</p>
    </section>
  </div>;
}

function OverviewRoute() {
  return <AdminOnly><OverviewPage /></AdminOnly>;
}

function RidesRoute() {
  return <AdminOnly><RidesPage /></AdminOnly>;
}

function DriversMapRoute() {
  return <AdminOnly><DriversMapPage /></AdminOnly>;
}

function PeopleRoute() {
  return <AdminOnly><PeoplePage /></AdminOnly>;
}

function ComplianceRoute() {
  return <AdminOnly><CompliancePage /></AdminOnly>;
}

function SupportRoute() {
  return <AdminOnly><SupportPage /></AdminOnly>;
}

function FinanceRoute() {
  return <AdminOnly><FinancePage /></AdminOnly>;
}

function SubscriptionsRoute() {
  return <AdminOnly><SubscriptionsPage /></AdminOnly>;
}

function RoutedErrorBoundary({ children }: { children: ReactNode }) {
  const [location] = useLocation();
  return <ErrorBoundary resetKey={location}>{children}</ErrorBoundary>;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <AuthProvider>
          <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, '')}>
            <Router />
          </WouterRouter>
        </AuthProvider>
        <Toaster />
      </TooltipProvider>
    </QueryClientProvider>
  );
}

export default App;
