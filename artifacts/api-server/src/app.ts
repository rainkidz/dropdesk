import express, { type Express } from "express";
import cors from "cors";
import pinoHttp from "pino-http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import router from "./routes";
import { logger } from "./lib/logger";

const app: Express = express();
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

app.use(
  pinoHttp({
    logger,
    serializers: {
      req(req) {
        return {
          id: req.id,
          method: req.method,
          url: req.url?.split("?")[0],
        };
      },
      res(res) {
        return {
          statusCode: res.statusCode,
        };
      },
    },
  }),
);
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.use("/api", router);

// Serve frontend static files from dist/public
const frontendDist = path.resolve(__dirname, "../../social-downloader/dist/public");
app.use(express.static(frontendDist));
app.get("{*splat}", (req, res, next) => {
  // Only serve index.html for non-API, non-file routes (SPA fallback)
  if (req.path.startsWith("/api") || req.path.includes(".")) return next();
  res.sendFile(path.join(frontendDist, "index.html"));
});

export default app;
