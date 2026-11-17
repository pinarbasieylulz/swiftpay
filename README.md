# SwiftPay — Money Transfer Case Study

SwiftPay dijital banka uygulaması için anlık para transferi özelliği. Bu repo backend (Spring Boot) ve frontend (React, opsiyonel) parçalarını içerir.

## Yapı

- `backend/` — Spring Boot 3, Java 21, PostgreSQL, JWT auth. Detaylar için `backend/README.md`.
- `frontend/` — React + TypeScript UI. Detaylar için `frontend/README.md`.

## Durum

- [x] Backend: transfer, hesap, istatistik API'leri, idempotency, limit kontrolleri, JWT auth, HTTPS/TLS, testler
- [x] Frontend: giriş, hesaplar/limitler, yeni transfer, transfer listesi/detayı, aylık istatistikler
