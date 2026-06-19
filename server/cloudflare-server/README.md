# bKash Sync Cloudflare Server

This is the backend server running on Cloudflare Workers, providing API endpoints to synchronize bKash transaction logs from devices to a Cloudflare D1 SQL database.

## Local Development Configuration

During local development using `wrangler dev`, environment variables and secrets (such as API keys) must be set in a local environment file. Cloudflare Wrangler uses a `.dev.vars` file for this purpose.

### Setting up `.dev.vars`

1. Copy the example file to create your own local secrets file:
   ```bash
   cp .dev.vars.example .dev.vars
   ```
2. Open `.dev.vars` and set your custom administrative API key:
   ```ini
   ADMIN_API_KEY=your_secure_local_admin_key
   ```
3. When running `npm run dev` or `npx wrangler dev`, Wrangler will automatically load this secret into the worker context under `env.ADMIN_API_KEY`.

> [!WARNING]
> Never commit `.dev.vars` to version control. It is ignored by default in the `.gitignore`.

### Production Secrets Configuration

In production, do not upload `.dev.vars`. Instead, bind the secret securely using Wrangler CLI:
```bash
npx wrangler secret put ADMIN_API_KEY
```

---

## Documentation

For full API documentation, refer to the [api_document.md](https://github.com/hmtanbir/bKash-Sync/blob/main/server/cloudflare-server/api_document.md) file.
