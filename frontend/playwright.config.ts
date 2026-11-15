import { defineConfig } from "@playwright/test";
import { existsSync } from "node:fs";
if (existsSync("../.env")) process.loadEnvFile("../.env");
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://localhost:5173",
    headless: true,
    launchOptions: process.env.CHROME_PATH
      ? { executablePath: process.env.CHROME_PATH }
      : {},
  },
  reporter: [["list"]],
});
