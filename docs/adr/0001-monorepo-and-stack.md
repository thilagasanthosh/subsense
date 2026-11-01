# ADR 0001: Monorepo, stack and component styling

- Status: Accepted
- Date: 2026-11-01

## Context

SubSense has a Spring Boot API, a React UI, end-to-end tests and, later, an MCP server. They change together: a new field touches the migration, the API, the form and a Cucumber scenario.

## Decision

- **One repository** with `backend/`, `frontend/`, `e2e/` and (Phase 4) `mcp-server/`, so one pull request carries a whole feature and CI checks it end to end.
- **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16. Package by feature (`subscription`, `currency`, `dashboard`).
- **Frontend:** React 19, TypeScript (strict), Vite, TanStack Query, React Hook Form + Zod, Recharts.
- **Styling:** plain CSS with design tokens (custom properties) instead of a component library such as Mantine. The UI is small; one stylesheet keeps the bundle light and avoids a dependency (KISS, YAGNI). Revisit if the UI grows significantly.
- **Money** travels as strings (`{"amount": "18.00", "currency": "GBP"}`) and is `BigDecimal` / `NUMERIC` everywhere it is calculated or stored.

## Consequences

- A single CI pipeline runs backend, frontend and end-to-end checks as required status checks.
- Docker images are still built and published per service, so they can be deployed independently.
