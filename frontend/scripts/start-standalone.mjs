import { cpSync, existsSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const projectRoot = process.cwd();
const standaloneRoot = resolve(projectRoot, ".next/standalone");

function copyBuildAsset(source, target) {
  if (existsSync(source) && !existsSync(target)) {
    mkdirSync(resolve(target, ".."), { recursive: true });
    cpSync(source, target, { recursive: true });
  }
}

copyBuildAsset(resolve(projectRoot, "public"), resolve(standaloneRoot, "public"));
copyBuildAsset(
  resolve(projectRoot, ".next/static"),
  resolve(standaloneRoot, ".next/static"),
);

process.env.HOSTNAME ??= "0.0.0.0";
process.chdir(standaloneRoot);
await import(pathToFileURL(resolve(standaloneRoot, "server.js")).href);
