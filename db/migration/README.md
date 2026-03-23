# DB Migration Guide (P0)

## Naming
- Forward migration: `V{yyyyMMddHHmm}__{desc}.sql`
- Rollback migration: `R{yyyyMMddHHmm}__{desc}.sql`

## Apply order
1. Apply all `V*.sql` files in lexical order.
2. Verify core tables and indexes.

## Rollback order
1. Apply rollback scripts in reverse order of matching forward migrations.
2. Validate data consistency and service health.

## Current baseline
- `V202603221400__p0_core_schema.sql`
- `V202603221410__p0_indexes.sql`
- `V202603221420__p0_domain_and_fact_schema.sql`
