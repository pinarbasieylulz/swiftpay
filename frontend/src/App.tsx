import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { NewTransferPage } from './pages/NewTransferPage';
import { TransferListPage } from './pages/TransferListPage';
import { TransferDetailPage } from './pages/TransferDetailPage';
import { StatisticsPage } from './pages/StatisticsPage';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/transfers/new" element={<NewTransferPage />} />
              <Route path="/transfers" element={<TransferListPage />} />
              <Route path="/transfers/:id" element={<TransferDetailPage />} />
              <Route path="/statistics" element={<StatisticsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
