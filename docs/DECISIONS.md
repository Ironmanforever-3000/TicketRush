# Design Decisions

## 001 — Child-to-parent JPA relationships
We use mostly unidirectional child-to-parent relationships instead of bidirectional collections.

Reason:
- avoid huge object graphs
- reduce accidental lazy loading
- make query intent explicit
- reduce serialization problems

## 002 — Flyway owns schema changes
JPA Hibernate does not create schema. Flyway migrations are the source of schema evolution.

## 003 — Integer money representation
Money is stored as integer paise/cents rather than floating point numbers.
