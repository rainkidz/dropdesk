---
name: OpenAPI codegen compatibility
description: OpenAPI schema choices that keep this workspace's generated Zod package type-safe.
---

The current Orval/Zod generation path is backed by Zod 3 APIs, so OpenAPI `format: uri` and integer schemas can generate unsupported `zod.url()` and `zod.int()` calls. Prefer plain strings and numbers in the contract, then enforce URL and integer semantics at the server boundary.

**Why:** The first downloader contract generated successfully but the chained library typecheck failed on those newer Zod helpers.

**How to apply:** When adding new OpenAPI fields, favor generator-compatible primitives unless the generated output is confirmed to support the richer format.