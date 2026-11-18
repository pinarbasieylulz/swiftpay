# SwiftPay — Frontend

Backend'deki transfer, hesap ve istatistik API'leri için React arayüzü (nice-to-have kapsamında).

## Tech Stack

- React 19 + TypeScript, Vite
- react-router-dom (routing)
- Harici UI kütüphanesi yok — sade, elle yazılmış CSS
- State yönetimi: React Context (auth) + yerel `useState`/`useEffect`; kapsam küçük olduğu için Redux/Zustand gibi bir kütüphane eklenmedi

## Çalıştırma

Backend'in `https://localhost:8443` üzerinde ayakta olması gerekir (bkz. `../backend/README.md`).

```bash
npm install
npm run dev
```

Tarayıcıda `http://localhost:5173` açılır.

**Önemli — self-signed sertifika:** Backend geliştirme ortamında self-signed bir TLS sertifikası kullanıyor. Tarayıcı, farklı bir origin'deki (API) bu sertifikaya güvenmediği için ilk kullanımda `https://localhost:8443` adresini tarayıcıda bir kez açıp sertifika uyarısını kabul etmeniz gerekir; aksi halde frontend'den API çağrıları "ağ hatası" olarak başarısız olur.

**Backend'i `SSL_ENABLED=false` ile çalıştırdıysanız** (örn. Windows'ta `keytool`/bash olmadığı için `generate-keystore.sh` çalıştıramadıysanız — bkz. `../backend/README.md`), backend düz HTTP'ye düşer ve `https://localhost:8443` artık yanıt vermez. Bu durumda `frontend/.env` içindeki `VITE_API_BASE_URL` değerini de `http://localhost:8443/api` olarak değiştirmeniz gerekir — ikisi birbiriyle eşleşmeli, aksi halde istekler başarısız olur.

## Demo kullanıcılar

`alice` / `Passw0rd!` ve `bob` / `Passw0rd!` (bkz. backend seed verisi).

## Sayfalar

- `/login` — giriş
- `/` — hesaplar + kalan günlük/aylık limitler
- `/transfers/new` — yeni transfer formu (client-side IBAN checksum + tutar validasyonu, idempotency key otomatik üretilir)
- `/transfers` — transfer listesi + tarih aralığı filtresi
- `/transfers/:id` — transfer detayı
- `/statistics` — son 6 aylık istatistik tablosu

## Tasarım kararları / varsayımlar

- API base URL `.env` üzerinden (`VITE_API_BASE_URL`) yapılandırılır, varsayılan `https://localhost:8443/api`.
- JWT `localStorage`'da tutulur; 401 yanıtı alındığında otomatik olarak login'e yönlendirilir.
- Her transfer denemesinde `crypto.randomUUID()` ile yeni bir idempotency key üretilir; backend hata mesajları `errorCode`'a göre kullanıcı dostu Türkçe mesajlara çevrilir (bkz. `src/api/errorMessages.ts`).
- IBAN validasyonu client tarafında da (TR + mod-97 checksum) yapılır, ancak backend validasyonu asıl otorite kalır — client validasyonu yalnızca UX içindir.
