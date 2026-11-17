import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyAccounts, getMyLimits } from '../api/accounts';
import type { AccountResponse, LimitResponse } from '../types/api';
import { ApiError } from '../api/client';

function formatMoney(value: number, currency = 'TRY') {
  return new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(value);
}

export function DashboardPage() {
  const [accounts, setAccounts] = useState<AccountResponse[] | null>(null);
  const [limits, setLimits] = useState<LimitResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([getMyAccounts(), getMyLimits()])
      .then(([acc, lim]) => {
        setAccounts(acc);
        setLimits(lim);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Veriler yüklenemedi.'));
  }, []);

  if (error) return <div className="result-banner error">{error}</div>;
  if (!accounts || !limits) return <div className="loading">Yükleniyor…</div>;

  return (
    <div className="stack">
      <section className="card">
        <div className="card-header">
          <h2>Hesaplarım</h2>
          <Link className="button" to="/transfers/new">Yeni Transfer</Link>
        </div>
        {accounts.length === 0 ? (
          <p className="empty-state">Henüz bir hesabınız yok.</p>
        ) : (
          <table>
            <thead>
              <tr><th>IBAN</th><th>Bakiye</th></tr>
            </thead>
            <tbody>
              {accounts.map((a) => (
                <tr key={a.id}>
                  <td>{a.iban}</td>
                  <td>{formatMoney(a.balance, a.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>Kalan Limitlerim</h2>
        <div className="limit-grid">
          <div>
            <span className="limit-label">Günlük kalan</span>
            <strong>{formatMoney(limits.dailyRemaining)}</strong>
            <span className="limit-total">/ {formatMoney(limits.dailyLimit)}</span>
          </div>
          <div>
            <span className="limit-label">Aylık kalan</span>
            <strong>{formatMoney(limits.monthlyRemaining)}</strong>
            <span className="limit-total">/ {formatMoney(limits.monthlyLimit)}</span>
          </div>
          <div>
            <span className="limit-label">Tek seferlik limit</span>
            <strong>{formatMoney(limits.perTransferLimit)}</strong>
          </div>
        </div>
      </section>
    </div>
  );
}
