# Order Execution Feature

This folder contains API clients and feature-level modules for:

1. `OrdersPoolPage`
2. `ExecutionWipPage`

`FE-B-01` now separates Orders Pool API calls into:

1. `ordersPoolQueryClient.js` for query/read operations.
2. `ordersPoolCommandClient.js` for command/write operations.
3. `ordersPoolClient.js` as compatibility export surface for existing pages.

`FE-B-02` separates reporting APIs into:

1. `reportingQueryClient.js` for report listing/filter queries.
2. `reportingCommandClient.js` for report submit/delete commands.
3. `executionWipClient.js` as compatibility export surface for existing pages.

`FE-B-03` first slice:

1. `ordersPoolService.js` extracts OrdersPoolPage API orchestration.
2. Page keeps rendering/state concerns; service handles API composition and command workflow.
