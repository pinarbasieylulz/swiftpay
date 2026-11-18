import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { listTransfers, listTransfersByDateRange } from '../api/transfers';
import type { TransferResponse } from '../types/api';
import { StatusBadge } from '../components/StatusBadge';
import { ApiError } from '../api/client';

const PAGE_SIZE = 10;

export function TransferListPage() {
  const [transfers, setTransfers] = useState<TransferResponse[] | null>(null);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  function load() {
    setError(null);
    const promise = startDate && endDate ? listTransfersByDateRange(startDate, endDate) : listTransfers();
    promise
      .then((result) => {
        setTransfers(result);
        setPage(0);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Transferler yüklenemedi.'));
  }

  useEffect(load, []);

  function handleFilter(e: FormEvent) {
    e.preventDefault();
    load();
  }

  const totalPages = transfers ? Math.max(1, Math.ceil(transfers.length / PAGE_SIZE)) : 1;
  const pageItems = transfers ? transfers.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE) : [];

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
        <div className="card-header">
          <h2>Transferlerim</h2>
          {transfers && transfers.length > 0 && (
            <span className="limit-total">{transfers.length} transfer</span>
          )}
        </div>
        {error && <div className="result-banner error">{error}</div>}
        {!transfers ? (
          <div className="loading">Yükleniyor…</div>
        ) : transfers.length === 0 ? (
          <p className="empty-state">Bu kriterlere uyan transfer bulunamadı.</p>
        ) : (
          <>
            <table>
              <thead>
                <tr><th>Tarih</th><th>Alıcı IBAN</th><th>Tutar</th><th>Durum</th><th></th></tr>
              </thead>
              <tbody>
                {pageItems.map((t) => (
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
            {totalPages > 1 && (
              <div className="pagination">
                <button type="button" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>
                  &larr; Önceki
                </button>
                <span className="limit-total">Sayfa {page + 1} / {totalPages}</span>
                <button type="button" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>
                  Sonraki &rarr;
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
