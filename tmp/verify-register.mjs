import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const source = "target/report-verification/university-roll-call-register.xlsx";
const outputDir = "outputs/019fb3cf-0693-7101-9edf-87a66a3674a8";
const previewDir = "tmp/spreadsheets";

const input = await FileBlob.load(source);
const workbook = await SpreadsheetFile.importXlsx(input);
const sheets = await workbook.inspect({
  kind: "sheet",
  include: "id,name",
  maxChars: 2000,
});
console.log("SHEETS");
console.log(sheets.ndjson);

const firstSheet = workbook.worksheets.getItemAt(0);
const region = await workbook.inspect({
  kind: "region",
  sheetId: firstSheet.name,
  range: "A1:R12",
  maxChars: 7000,
});
console.log("REGION");
console.log(region.ndjson);

const formulaErrors = firstSheet.getUsedRange().values
  .flat()
  .filter((value) => typeof value === "string" && /^#(REF!|DIV\/0!|VALUE!|NAME\?|N\/A)$/.test(value));
if (formulaErrors.length > 0) {
  throw new Error(`Spreadsheet contains formula errors: ${formulaErrors.join(", ")}`);
}
console.log("FORMULA_ERRORS=0");

await fs.mkdir(previewDir, { recursive: true });
const preview = await workbook.render({
  sheetName: firstSheet.name,
  autoCrop: "all",
  scale: 1,
  format: "png",
});
await fs.writeFile(
  `${previewDir}/university-roll-call-register.png`,
  new Uint8Array(await preview.arrayBuffer()),
);

await fs.mkdir(outputDir, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(`${outputDir}/university-roll-call-register.xlsx`);
console.log("EXPORTED");
