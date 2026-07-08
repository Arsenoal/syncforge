/**
 * Headless browser runner for :sample-web ?smoke=1 (used by root webE2e Gradle task).
 * Uses system Chrome/Chromium via puppeteer-core (no bundled browser download).
 */
import { createServer } from "node:http";
import { access } from "node:fs/promises";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webpackDir = path.resolve(__dirname, "../build/kotlin-webpack/js/productionExecutable");
const indexHtmlPath = path.resolve(__dirname, "../build/processedResources/js/main/index.html");
const staticPort = Number(process.env.WEB_SMOKE_PORT ?? "9876");
const pageUrl = `http://127.0.0.1:${staticPort}/?smoke=1`;
const timeoutMs = Number(process.env.WEB_SMOKE_TIMEOUT_MS ?? "120000");
const chromeCandidates = [
    process.env.CHROME_PATH,
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
].filter(Boolean);

async function loadPuppeteerCore() {
    return import("puppeteer-core");
}

function startStaticServer(root, indexPath) {
    const server = createServer(async (req, res) => {
        const urlPath = req.url?.split("?")[0] ?? "/";
        const filePath = urlPath === "/" || urlPath === "/index.html"
            ? indexPath
            : path.join(root, urlPath.slice(1));
        try {
            const data = await readFile(filePath);
            const ext = path.extname(filePath);
            const type =
                ext === ".html" ? "text/html" :
                ext === ".js" ? "application/javascript" :
                ext === ".wasm" ? "application/wasm" :
                "application/octet-stream";
            res.writeHead(200, { "Content-Type": type });
            res.end(data);
        } catch {
            res.writeHead(404);
            res.end("Not found");
        }
    });
    return new Promise((resolve, reject) => {
        server.listen(staticPort, "127.0.0.1", () => resolve(server));
        server.on("error", reject);
    });
}

async function resolveChromeExecutable() {
    for (const candidate of chromeCandidates) {
        try {
            await access(candidate);
            return candidate;
        } catch {
            // try next candidate
        }
    }
    throw new Error("No Chrome/Chromium executable found — set CHROME_PATH");
}

async function runWithPuppeteer(puppeteer, baseUrl) {
    const browser = await puppeteer.launch({
        headless: true,
        executablePath: await resolveChromeExecutable(),
        args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"],
    });
    try {
        const page = await browser.newPage();
        page.on("console", (msg) => console.log(`[browser] ${msg.text()}`));
        page.on("pageerror", (error) => console.error("[browser:error]", error));
        await page.evaluateOnNewDocument((url) => {
            globalThis.MOCK_SERVER_BASE_URL = url;
            globalThis.SMOKE_MODE = true;
        }, baseUrl);
        await page.goto(pageUrl, { waitUntil: "networkidle0", timeout: timeoutMs });
        await page.waitForFunction(
            () => globalThis.__syncforgeSmokeResult != null,
            { timeout: timeoutMs },
        );
        const result = await page.evaluate(() => globalThis.__syncforgeSmokeResult);
        if (result !== "ok") {
            throw new Error(`web-smoke result: ${result}`);
        }
        console.log("web-smoke-runner: ok");
    } finally {
        await browser.close();
    }
}

async function main() {
    const baseUrl = process.env.MOCK_SERVER_BASE_URL ?? "http://127.0.0.1:8080";
    const puppeteerModule = await loadPuppeteerCore();
    const puppeteer = puppeteerModule.default ?? puppeteerModule;

    const server = await startStaticServer(webpackDir, indexHtmlPath);
    try {
        await runWithPuppeteer(puppeteer, baseUrl);
    } finally {
        server.close();
    }
}

main().catch((error) => {
    console.error("web-smoke-runner failed:", error);
    process.exit(1);
});