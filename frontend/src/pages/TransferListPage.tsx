import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { listTransfers, listTransfersByDateRange } from '../api/transfers';
import type { TransferResponse } from '../types/api';
import { StatusBadge } from '../components/StatusBadge';
import { ApiError } from '../api/client';

export function TransferListPage() {
  const [transfers, setTransfers] = useState<TransferResponse[] | null>(null);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [error, setError] = useState<string | null>(null);

  function load() {
    setError(null);
    const promise = startDate && endDate ? listTransfersByDateRange(startDate, endDate) : listTransfers();
    promise.then(setTransfers).catch((err) => setError(err instanceof ApiError ? err.message : 'Transferler yüklenemedi.'));
  }

  useEffect(load, []);

  function handleFilter(e: FormEvent) {
    e.preventDefault();
    load();
  }

  return (
    <div className="stack">
      <form className="card filter-bar" onSubmit={handleFilter}>
        <label>
          Başlangıç
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </label>
        <label>
          Bitiş
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        </label>
        <button type="submit">Filtrele</button>
      </form>

      <section className="card">
        <h2>Transferlerim</h2>
        {error && <div className="result-banner error">{error}</div>}
        {!transfers ? (
          <div className="loading">Yükleniyor…</div>
        ) : transfers.length === 0 ? (
          <p className="empty-state">Bu kriterlere uyan transfer bulunamadı.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Tarih</th><th>Alıcı IBAN</th><th>Tutar</th><th>Durum</th><th></th></tr>
            </thead>
            <tbody>
              {transfers.map((t) => (
                <tr key={t.id}>
                  <td>{new Date(t.createdAt).toLocaleString('tr-TR')}</td>
                  <td>{t.destinationIban}</td>
                  <td>{t.amount} {t.currency}</td>
                  <td><StatusBadge status={t.status} /></td>
                  <td><Link to={`/transfers/${t.id}`}>Detay</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
