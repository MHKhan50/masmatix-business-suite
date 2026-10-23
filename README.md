# Masmatix Business Suite

Small-business systems (shop till, bookings, clinic, gym, hotel and more) built on one shared base, by **Masmatix Innovations**.

This repository rebuilds, from scratch, the kinds of systems our team has delivered for shops and service businesses. It contains no client code and no client data: every name, product and barcode in the sample data is invented and marked "(sample)".

**Status:** the shared base and five systems are in place: **Shop POS**, **Salon and spa booking**, **Clinic prescriptions**, **Gym membership** and **Hotel booking**. More systems are added as spec files (see [Roadmap](#roadmap)).

| Till screen | Printed receipt (PDF) |
|---|---|
| ![Till screen of the Shop POS](docs/images/till.png) | ![Receipt PDF for one cash sale](docs/images/receipt.png) |

*Screenshots from a local run with the invented sample data.*

## What is in it

| Part | Tech | What it does |
|---|---|---|
| `backend/` | Java 21, Spring Boot 3, Spring Security (JWT), JPA, PostgreSQL | REST API, roles, audit log, PDF receipts, CSV export |
| `frontend/` | React 18, TypeScript, Vite | Till, salon diary, prescription writer, gym desk, hotel desk, record screens |
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

### Salon and spa booking

- Diary per stylist for any day, with free start times for the chosen treatment (15-minute steps, 09:00 to 18:00).
- No double booking: the stylist row is locked while a booking is saved, and overlaps are checked with half-open times, so 10:00 to 10:45 and 10:45 to 11:30 can both be booked.
- Rejects past times, times off the quarter hour and treatments that would run past closing.
- Cancel or mark done; a cancelled slot becomes free again.
- Stylists and treatments (length, price, category) come from `specs/salon.json`.

### Clinic prescription writer

- Prescription with up to 10 medicines (dose, frequency, days, instructions), printed as an A4 PDF with a signature line.
- Allergy warning: if a medicine's name or ingredient matches the patient's recorded allergies, saving is refused until the prescriber gives an override reason. The override is printed and written to the audit log.
- The patient's name, date of birth and allergies are copied into the prescription, so later edits to the patient record do not change an issued prescription.
- Only `MANAGER` and `ADMIN` (standing in for the prescriber) can write; `STAFF` can view and print.
- Patients and the medicine list come from `specs/clinic.json`.

### Gym membership and fees

- Front desk check-in by member code (typed or scanned). Expired, frozen and unknown members are blocked, and every attempt is logged.
- A membership is valid up to and including its expiry date.
- Taking fees: paying early extends from the current expiry (no paid days lost); paying after expiry starts from today. Month ends follow the calendar (31 January plus one month is 28 or 29 February).
- Fee receipt PDF, list of memberships ending in the next 7 days, payment history per member.
- Plans (months, fee) come from `specs/gym.json`.

### Hotel booking and check-in

- Availability search by dates and number of guests.
- A room cannot be sold twice for the same night: the room row is locked while booking, and stays use half-open dates, so one guest can leave on the day the next arrives.
- Checks room capacity, past dates and a 30-night limit; price is nightly rate x nights.
- Front desk lists today's arrivals and guests in house. Check-in only from the arrival date; check-out only after check-in; only reservations can be cancelled (managers).
- A4 invoice PDF per booking. Rooms come from `specs/hotel.json`.

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

Try a sample barcode on the till: `2000000000084`. On the gym desk, try member codes `GYM-0001` (active), `GYM-0002` (expired) and `GYM-0003` (frozen). Sample barcodes use the 200 to 299 in-store prefix range, so they do not belong to any real product.

## Tests

| Where | What is tested | Command |
|---|---|---|
| `backend/src/test` | POS: money rounding, login, barcode lookup, sale totals, stock, receipt, role rules, generated CRUD and CSV, audit. Salon: overlap, back-to-back, opening hours, free slots, cancel. Clinic: allergy block and override, prescriber role, print. Gym: expiry day, early and late renewal, month ends, frozen and unknown codes. Hotel: double booking, same-day turnover, capacity, check-in and check-out order, invoice | `cd backend && mvn test` |
| `frontend/src/lib/*.test.ts` | cart totals, stay nights and totals, and renewal dates match the backend numbers | `cd frontend && npm test` |
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
- [x] Salon and spa booking: diary, free slots, no double booking
- [x] Clinic prescription writer: allergy warning, printable prescription
- [x] Gym membership and fees: entry blocked after expiry, fee receipts
- [x] Hotel booking and check-in: availability, no double booking, invoices

The remaining systems listed on [masmatixinnovations.co.uk](https://masmatixinnovations.co.uk/#systems) will be added as spec files.

## Limitations

- Tables are created by Hibernate (`ddl-auto: update`). A production install should use versioned migrations (Flyway).
- Card payments are recorded, not processed: there is no payment terminal integration.
- The clinic module is sample software, not a medical device. The allergy check is a plain text match against the patient's recorded allergies; it does not replace a drug database or clinical judgement, and printed prescriptions say they are not valid for dispensing.
- Salon opening hours (09:00 to 18:00) and the hotel's 30-night limit are constants in code, not settings yet.
- Receipts are plain sales receipts. They are not certified for any tax authority's e-invoicing scheme.
- The demo users share one password from `.env`. Change it, and the JWT secret, before any real use.

## Licence

MIT. Copyright (c) 2026 Masmatix Innovations Ltd.
