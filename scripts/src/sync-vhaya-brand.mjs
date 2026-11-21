import { execFileSync } from "node:child_process";
import { mkdtemp, readFile, writeFile, mkdir, copyFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const sourcePath = path.join(workspaceRoot, "brand/vhaya-logo-mark.svg");
const webFaviconPath = path.join(workspaceRoot, "artifacts/vhaya-admin/public/favicon.svg");
const androidResPath = path.join(workspaceRoot, "android/app/src/main/res");
const svg = await readFile(sourcePath, "utf8");

function attribute(tag, name) {
  const match = tag.match(new RegExp(`\\b${name}="([^"]+)"`));
  if (!match) throw new Error(`The shared logo is missing its ${name} attribute.`);
  return match[1];
}

const viewBox = svg.match(/<svg\b[^>]*\bviewBox="([^"]+)"/)?.[1];
const backgroundTag = svg.match(/<rect\b[^>]*\bid="background"[^>]*\/>/)?.[0];
const backgroundColor = backgroundTag && attribute(backgroundTag, "fill");
const viewBoxParts = viewBox?.split(/\s+/).map(Number);
if (!viewBoxParts || viewBoxParts.length !== 4 || !backgroundColor) {
  throw new Error("The shared Vhaya logo must have a square viewBox and a background rectangle.");
}
const [, , viewportWidth, viewportHeight] = viewBoxParts;
if (viewportWidth !== viewportHeight) {
  throw new Error("The shared Vhaya logo must use a square viewBox.");
}

const pathTags = [...svg.matchAll(/<path\b[^>]*\/>/g)].map(([tag]) => tag);
if (pathTags.length === 0) throw new Error("The shared Vhaya logo has no vector paths.");

const androidPaths = pathTags.map((tag) => {
  const pathData = attribute(tag, "d");
  const fill = tag.match(/\bfill="([^"]+)"/)?.[1];
  const stroke = tag.match(/\bstroke="([^"]+)"/)?.[1];
  const strokeWidth = tag.match(/\bstroke-width="([^"]+)"/)?.[1];
  const lineCap = tag.match(/\bstroke-linecap="([^"]+)"/)?.[1];
  const lineJoin = tag.match(/\bstroke-linejoin="([^"]+)"/)?.[1];
  if (fill && fill !== "none") {
    return `    <path android:fillColor="${fill}" android:pathData="${pathData}" />`;
  }
  if (!stroke || !strokeWidth) {
    throw new Error(`Logo path "${attribute(tag, "id")}" needs a fill or a complete stroke.`);
  }
  return [
    "    <path",
    '        android:fillColor="#00000000"',
    `        android:strokeColor="${stroke}"`,
    `        android:strokeWidth="${strokeWidth}"`,
    lineCap ? `        android:strokeLineCap="${lineCap}"` : null,
    lineJoin ? `        android:strokeLineJoin="${lineJoin}"` : null,
    `        android:pathData="${pathData}" />`,
  ].filter(Boolean).join("\n");
});

const rootOpen = `<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="${viewportWidth}" android:viewportHeight="${viewportHeight}">`;
const rootClose = "</vector>\n";
const fullBackgroundPath = `    <path android:fillColor="${backgroundColor}" android:pathData="M0,0h${viewportWidth}v${viewportHeight}h-${viewportWidth}z" />`;
const vectorContent = androidPaths.join("\n");
const foregroundXml = `${rootOpen}\n${vectorContent}\n${rootClose}`;
const fullMarkXml = `${rootOpen}\n${fullBackgroundPath}\n${vectorContent}\n${rootClose}`;
const adaptiveBackgroundXml = `<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <solid android:color="${backgroundColor}" />\n</shape>\n`;

await mkdir(path.join(androidResPath, "drawable"), { recursive: true });
await mkdir(path.dirname(webFaviconPath), { recursive: true });
await Promise.all([
  copyFile(sourcePath, webFaviconPath),
  writeFile(path.join(androidResPath, "drawable/ic_launcher_foreground.xml"), foregroundXml),
  writeFile(path.join(androidResPath, "drawable/vhaya_mark_full.xml"), fullMarkXml),
  writeFile(path.join(androidResPath, "drawable/ic_launcher_background.xml"), adaptiveBackgroundXml),
]);

const temporaryDirectory = await mkdtemp(path.join(os.tmpdir(), "vhaya-brand-"));
const densities = [
  ["mipmap-mdpi", 48],
  ["mipmap-hdpi", 72],
  ["mipmap-xhdpi", 96],
  ["mipmap-xxhdpi", 144],
  ["mipmap-xxxhdpi", 192],
];

try {
  for (const [directory, size] of densities) {
    const targetDirectory = path.join(androidResPath, directory);
    await mkdir(targetDirectory, { recursive: true });
    const squarePath = path.join(targetDirectory, "ic_launcher.png");
    const roundPath = path.join(targetDirectory, "ic_launcher_round.png");
    const maskPath = path.join(temporaryDirectory, `circle-${size}.png`);
    const center = size / 2;
    const radius = center - 1;

    execFileSync("magick", [sourcePath, "-resize", `${size}x${size}!`, squarePath], { stdio: "inherit" });
    execFileSync("magick", [
      "-size", `${size}x${size}`, "xc:none", "-fill", "white",
      "-draw", `circle ${center},${center} ${center},${center - radius}`,
      maskPath,
    ], { stdio: "inherit" });
    execFileSync("magick", [
      sourcePath, "-resize", `${size}x${size}!`, "-alpha", "on",
      maskPath, "-compose", "CopyOpacity", "-composite", roundPath,
    ], { stdio: "inherit" });
  }
} catch (error) {
  throw new Error("Could not render the shared logo into Android launcher icons. Ensure ImageMagick is available.", { cause: error });
} finally {
  await import("node:fs/promises").then(({ rm }) => rm(temporaryDirectory, { recursive: true, force: true }));
}

console.log("Synchronized Vhaya's shared logo to the web favicon and Android icons.");
