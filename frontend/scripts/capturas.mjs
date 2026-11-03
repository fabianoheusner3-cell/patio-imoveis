import { chromium } from "@playwright/test";
import fs from "node:fs";
fs.mkdirSync("capturas", { recursive: true });
const browser = await chromium.launch({
  headless: true,
  executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe",
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
await page.goto("http://127.0.0.1:5173/");
await page.locator(".property-card").first().waitFor();
await page.screenshot({ path: "capturas/vitrine.png", fullPage: true });
await page.setViewportSize({ width: 390, height: 844 });
await page.screenshot({ path: "capturas/celular.png", fullPage: true });
await browser.close();
