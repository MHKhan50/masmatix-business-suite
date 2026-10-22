# Masmatix Business Suite

Small-business systems (shop till, bookings, clinic, gym, hotel and more) built on one shared base, by **Masmatix Innovations**.

This repository rebuilds, from scratch, the kinds of systems our team has delivered for shops and service businesses. It contains no client code and no client data: every name, product and barcode in the sample data is invented and marked "(sample)".

**Status:** the shared base and the **Shop POS** system are in place. The other systems are added step by step (see [Roadmap](#roadmap)).

| Till screen | Printed receipt (PDF) |
|---|---|
| ![Till screen of the Shop POS](docs/images/till.png) | ![Receipt PDF for one cash sale](docs/images/receipt.png) |

*Screenshots from a local run with the invented sample data.*

## What is in it

| Part | Tech | What it does |
|---|---|---|
| `backend/` | Java 21, Spring Boot 3, Spring Security (JWT), JPA, PostgreSQL | REST API, roles, audit log, PDF receipts, CSV export |
| `frontend/` | React 18, TypeScript, Vite | Till screen, product and record screens, sales and daily report |
| `tools/generator/` | Python 3.9+ (standard library only) | Builds entity, repository, REST controller and screen definition from a JSON spec |
| `specs/` | JSON | One spec file per system |
| `docker-compose.yml` | Docker | Database, API and web app with one command |

### Shared base (written once, used by every system)

- **Login and roles:** `ADMIN`, `MANAGER`, `STAFF`. Stateless JWT tokens, BCrypt passwords.
- **Generic CRUD:** list, read, create, update, delete, CSV export for any entity, with the same role rules everywhere (staff read, managers edit, admins delete).
- **Audit log:** every create, update, delete, sale and login is recorded with user and time.
- **PDF documents:** one builder for receipts, invoices and prescriptions (80 mm receipt or A4).
- **CSV export:** with protection against spreadsheet formula injection.
- **Errors:** RFC 7807 problem responses, so the UI shows one consistent message format.

### Shop POS

- Barcode lookup: a USB scanner types the code and presses Enter; the till adds the item.
- Cart with quantity limits based on stock.
- Checkout for cash (tendered and change) or card. Stock is locked per row during checkout, so two tills cannot sell the last item twice.
- Tax per product (e.g. 20% standard VAT, 0% for zero-rated food), rounded half-up per line. The backend (`PricingCalculator.java`) and the till screen (`cart.ts`) use the same rule, and both have tests with the same numbers.
- Printable PDF receipt for each sale.
- Daily takings report (count, subtotal, tax, cash vs card).
- Low-stock list (`/api/pos/products/low-stock`).
- Suppliers and loyalty customers, built by the generator from `specs/pos.json`.

## Run it

Needs [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```bash
cp .env.example .env
# put a real secret in .env:  APP_JWT_SECRET=$(openssl rand -hex 32)
docker compose up --build
```

- Web app: http://localhost:3000 (users `admin`, `manager`, `staff`, password = `APP_DEMO_PASSWORD` from `.env`)
- API docs (OpenAPI / Swagger UI): http://localhost:8080/swagger-ui.html

The first build downloads Maven and npm packages and runs all tests inside the build, so it takes a few minutes.

Try a sample barcode on the till: `2000000000084`. Sample barcodes use the 200 to 299 in-store prefix range, so they do not belong to any real product.

## Tests

| Where | What is tested | Command |
|---|---|---|
| `backend/src/test` | money rounding; login; token required; barcode lookup; cash sale totals, change and stock; receipt PDF; out-of-stock and short-cash rejection; role rules; generated module CRUD, validation and CSV; audit access | `cd backend && mvn test` |
| `frontend/src/lib/cart.test.ts` | cart totals match the backend numbers; half-up tax; stock limits | `cd frontend && npm test` |
| `tools/generator/test_generate.py` | generated code shape; bad specs rejected | `python3 -m unittest discover -s tools/generator` |

The Docker build runs the backend and frontend tests, so `docker compose up --build` fails if a test fails.

## Adding a system with the generator

A system is a JSON file in `specs/`. Example field types: `string`, `text`, `int`, `decimal`, `bool`, `date`, `email`, `phone`, `enum`.

```json
{
  "system": "gym",
  "title": "Gym membership",
  "entities": [
    { "name": "Member", "label": "Members", "path": "members",
      "fields": [
        { "name": "fullName", "type": "string", "required": true },
        { "name": "plan", "type": "enum", "values": ["MONTHLY", "ANNUAL"] },
        { "name": "expiresOn", "type": "date" }
      ] }
  ]
}
```

```bash
python3 tools/generator/generate.py          # writes the Java and the screen definition
python3 tools/generator/generate.py --check  # fails if generated files are out of date
```

Each entity gets a table, validation, REST endpoints under `/api/<system>/<path>`, OpenAPI docs, CSV export, audit entries and a screen in the web app. Hand-written features (like the till) sit beside the generated code.

## Roadmap

Systems with hand-written features on top of the generated base:

- [x] Shop POS: barcode till, receipts, stock, daily report
- [ ] Salon and spa booking: calendar, no double booking
- [ ] Clinic prescription writer: printable prescription
- [ ] Gym membership and fees: entry blocked after expiry
- [ ] Hotel booking and check-in: room availability

The remaining systems listed on [masmatixinnovations.co.uk](https://masmatixinnovations.co.uk/#systems) will be added as spec files.

## Limitations

- Tables are created by Hibernate (`ddl-auto: update`). A production install should use versioned migrations (Flyway).
- Card payments are recorded, not processed: there is no payment terminal integration.
- Receipts are plain sales receipts. They are not certified for any tax authority's e-invoicing scheme.
- The demo users share one password from `.env`. Change it, and the JWT secret, before any real use.

## Licence

MIT. Copyright (c) 2026 Masmatix Innovations Ltd.
