import { useEffect, useState } from 'react';
import { getMonthlyStatistics } from '../api/transfers';
import type { MonthlyStatisticResponse } from '../types/api';
import { ApiError } from '../api/client';

const monthNames = [
  'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
  'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık',
];

export function StatisticsPage() {
  const [stats, setStats] = useState<MonthlyStatisticResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getMonthlyStatistics()
      .then(setStats)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'İstatistikler yüklenemedi.'));
  }, []);

  if (error) return <div className="result-banner error">{error}</div>;
  if (!stats) return <div className="loading">Yükleniyor…</div>;

  return (
    <div className="card">
      <h2>Aylık İstatistikler</h2>
      <table>
        <thead>
          <tr><th>Ay</th><th>Transfer Sayısı</th><th>Toplam Tutar</th></tr>
        </thead>
        <tbody>
          {stats.map((s) => {
            const [year, month] = s.month.split('-').map(Number);
            return (
              <tr key={s.month}>
                <td>{monthNames[month - 1]} {year}</td>
                <td>{s.totalTransferCount}</td>
                <td>{new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(s.totalTransferredAmount)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
