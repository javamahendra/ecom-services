import { FileSpreadsheet, Upload, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import LoadingState from "../../../components/LoadingState";
import { getStockImportHistory, importProductStock } from "../../../services/productService";

export function StockImportPanel({ token, onImported }) {
  const currentUser = useSelector((state) => state.auth);
  const userName = currentUser?.username || currentUser?.email || "you";
  const previousImportStatuses = useRef(new Map());
  const initialHistoryLoaded = useRef(false);
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadPercent, setUploadPercent] = useState(0);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyPages, setHistoryPages] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyRefresh, setHistoryRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    let polling = false;
    const loadHistory = async (showLoading = false) => {
      if (polling) return;
      polling = true;
      if (showLoading) setHistoryLoading(true);
      try {
        const response = await getStockImportHistory(historyPage, token);
        if (active) {
          const entries = response?.content || [];
          const isCurrentUser = (entry) => [currentUser?.username, currentUser?.email]
            .filter(Boolean)
            .some((value) => value.toLowerCase() === (entry.uploadedBy || "").toLowerCase());
          if (initialHistoryLoaded.current && historyPage === 0) {
            entries.forEach((entry) => {
              const previousStatus = previousImportStatuses.current.get(entry.id);
              const isActive = ["PROCESSING", "APPLYING"].includes(entry.status);
              if (!previousStatus && !isCurrentUser(entry)) {
                if (isActive) {
                  toast.info(`${entry.uploadedBy} started stock upload: ${entry.fileName}`);
                } else {
                  const message = `${entry.uploadedBy} finished ${entry.fileName}: ${entry.successRows} succeeded, ${entry.failureRows} failed.`;
                  if (entry.failureRows > 0) toast.warn(message);
                  else toast.success(message);
                }
              } else if (previousStatus && ["PROCESSING", "APPLYING"].includes(previousStatus) && !isActive
                && !isCurrentUser(entry)) {
                const message = `${entry.uploadedBy} finished ${entry.fileName}: ${entry.successRows} succeeded, ${entry.failureRows} failed.`;
                if (entry.failureRows > 0) toast.warn(message);
                else toast.success(message);
              }
              previousImportStatuses.current.set(entry.id, entry.status);
            });
          } else if (!initialHistoryLoaded.current) {
            entries.forEach((entry) => previousImportStatuses.current.set(entry.id, entry.status));
            if (historyPage === 0) initialHistoryLoaded.current = true;
          } else {
            entries.forEach((entry) => previousImportStatuses.current.set(entry.id, entry.status));
          }
          setHistory(entries);
          setHistoryPages(response?.totalPages || 0);
        }
      } catch (error) {
        if (active && showLoading) toast.error(error.message || "Could not load stock import history.");
      } finally {
        polling = false;
        if (active && showLoading) setHistoryLoading(false);
      }
    };
    loadHistory(true);
    const interval = window.setInterval(() => loadHistory(), 30_000);
    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [currentUser?.email, currentUser?.username, historyPage, historyRefresh, token]);

  const chooseFile = (nextFile) => {
    if (!nextFile) return;
    if (!/\.(csv|xlsx|xls)$/i.test(nextFile.name)) {
      setFile(null);
      setResult(null);
      toast.error("Choose a CSV or Excel file (.csv, .xlsx, .xls).");
      return;
    }
    if (nextFile.size > 10 * 1024 * 1024) {
      setFile(null);
      setResult(null);
      toast.error("Stock import files must be 10 MB or smaller.");
      return;
    }
    setFile(nextFile);
    setResult(null);
  };

  const upload = async () => {
    if (!file) {
      toast.error("Choose a stock file to upload.");
      return;
    }
    setUploading(true);
    setUploadPercent(0);
    toast.info(`Stock upload started by ${userName || "you"}: ${file.name}`);
    try {
      const imported = await importProductStock(file, token, (event) => {
        if (event.total) setUploadPercent(Math.min(100, Math.round((event.loaded * 100) / event.total)));
      });
      setResult(imported);
      setFile(null);
      onImported();
      setHistoryPage(0);
      setHistoryRefresh((current) => current + 1);
      const message = `Stock upload finished: ${imported.successRows} succeeded, ${imported.failureRows} failed.`;
      if (imported.failureRows > 0) toast.warn(message);
      else toast.success(message);
    } catch (error) {
      setHistoryPage(0);
      setHistoryRefresh((current) => current + 1);
      toast.error(`Stock upload failed: ${error.message || "Could not import stock."}`);
    } finally {
      setUploading(false);
    }
  };

  const activeImports = history.filter((item) => ["PROCESSING", "APPLYING"].includes(item.status));
  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <div className="flex items-center gap-2"><FileSpreadsheet size={17} className="text-[#52644f]" /><h2 className="text-lg font-medium text-[#354237]">Bulk stock update</h2></div>
      <p className="mt-1 text-xs text-[#858a80]">Upload CSV or Excel rows using each product code and color variant. Invalid files are rejected without applying changes.</p>
      <div
        onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
        onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragging(false); }}
        onDrop={(event) => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files?.[0]); }}
        className={`mt-4 flex flex-col items-center border border-dashed p-6 text-center transition-colors ${dragging ? "border-[#52644f] bg-[#f4f5f0]" : "border-[#cfd4c9] bg-[#fbfbf8]"}`}
      >
        <Upload size={20} className="text-[#71816d]" />
        <p className="mt-2 text-sm text-[#52644f]">Drop your stock sheet here</p>
        <p className="mt-1 text-xs text-[#858a80]">CSV, XLSX, or XLS · up to 10 MB · maximum 5,000 rows</p>
        <label className="mt-3 cursor-pointer border border-[#dfe2d9] bg-white px-3 py-2 text-xs text-[#52644f] hover:bg-[#f4f5f0]">
          Browse files
          <input type="file" accept=".csv,.xlsx,.xls" className="sr-only" onChange={(event) => { chooseFile(event.target.files?.[0]); event.target.value = ""; }} />
        </label>
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 text-xs text-[#687064]">
          {file ? <span className="truncate">{file.name} · {(file.size / 1024).toFixed(1)} KB</span> : <span>Columns: <code>productCode,color,stockCount</code></span>}
        </div>
        <div className="flex items-center gap-3">
          <a href="data:text/csv;charset=utf-8,productCode%2Ccolor%2CstockCount%0A" download="stock-import-template.csv"
            className="text-xs text-[#52644f] underline underline-offset-2">Download CSV template</a>
          <button type="button" disabled={!file || uploading} onClick={upload}
            className="h-9 bg-[#314638] px-4 text-xs font-medium text-white hover:bg-[#25372b] disabled:cursor-not-allowed disabled:opacity-50">
            {uploading ? "Uploading…" : "Update stock"}
          </button>
        </div>
      </div>
      {uploading && <div role="status" aria-live="polite" className="mt-4 border border-[#dfe2d9] bg-[#fbfbf8] p-3">
        <div className="flex items-center justify-between text-xs text-[#52644f]">
          <span>{uploadPercent < 100 ? "Uploading file…" : "File received; applying stock updates…"}</span>
          <span>{uploadPercent}%</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden bg-[#e4e6de]">
          <div className="h-full bg-[#52644f] transition-[width]" style={{ width: `${uploadPercent}%` }} />
        </div>
      </div>}
      {activeImports.length > 0 && <div className="mt-4 space-y-2" aria-live="polite">
        {activeImports.map((item) => (
          <div key={item.id} className="border border-blue-200 bg-blue-50 p-3 text-xs text-blue-900">
            <div className="flex flex-wrap justify-between gap-2">
              <span><strong>{item.uploadedBy}</strong> is uploading/processing <strong>{item.fileName}</strong></span>
              <span>{item.processedRows} / {item.totalRows} rows · {item.progressPercent}%</span>
            </div>
            <div className="mt-2 h-2 overflow-hidden bg-blue-100">
              <div className="h-full bg-blue-700 transition-[width]" style={{ width: `${item.progressPercent}%` }} />
            </div>
            <p className="mt-1">{item.successRows} succeeded · {item.failureRows} failed so far</p>
          </div>
        ))}
      </div>}
      {result && <div role="status" aria-live="polite" className={`mt-3 border p-3 text-xs ${result.failureRows > 0 ? "border-amber-200 bg-amber-50 text-amber-900" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
        Import {result.importId}: {result.totalRows} rows processed, {result.successRows} succeeded, {result.failureRows} failed.
        <ul className="mt-2 max-h-40 list-inside list-disc space-y-1 overflow-y-auto">
          {(result.updates || []).slice(0, 50).map((update, index) => (
            <li key={`${update.productCode}-${update.color}-${index}`}>
              Row {update.rowNumber}: {update.productCode} · {update.color}: {update.previousStockCount} → {update.stockCount}
            </li>
          ))}
        </ul>
        {result.updates?.length > 50 && <span className="mt-1 block">Showing the first 50 successful rows.</span>}
        {result.failures?.length > 0 && <div className="mt-3 border-t border-amber-200 pt-2">
          <p className="font-medium">Failed rows</p>
          <ul className="mt-1 max-h-32 list-inside list-disc space-y-1 overflow-y-auto">
            {result.failures.slice(0, 50).map((failure, index) => (
              <li key={`${failure.rowNumber}-${index}`}>Row {failure.rowNumber}: {failure.productCode} · {failure.color} — {failure.message}</li>
            ))}
          </ul>
          {result.failures.length > 50 && <span className="mt-1 block">Showing the first 50 failed rows.</span>}
        </div>}
      </div>}
      <div className="mt-6 border-t border-[#e4e6de] pt-5">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h3 className="text-sm font-medium text-[#354237]">Stock import history</h3>
            <p className="mt-1 text-xs text-[#858a80]">Uploader, time, source file, and product/variant stock changes.</p>
          </div>
          <button type="button" onClick={() => setHistoryRefresh((current) => current + 1)}
            className="text-xs text-[#52644f] underline underline-offset-2">Refresh history</button>
        </div>
        {historyLoading ? <LoadingState label="Loading import history…" compact /> : history.length === 0 ? (
          <p className="mt-3 text-xs text-[#858a80]">No stock uploads recorded yet.</p>
        ) : (
          <div className="mt-3 space-y-2">
            {history.map((item) => (
              <details key={item.id} className="border border-[#e4e6de] bg-[#fbfbf8] px-3 py-2">
                <summary className="flex cursor-pointer flex-wrap items-center justify-between gap-x-4 gap-y-1 text-xs text-[#52644f]">
                  <span className="min-w-0 truncate font-medium">{item.fileName}</span>
                  <span>{item.status === "COMPLETED" || item.status === "COMPLETED_WITH_ERRORS" || item.status === "FAILED"
                    ? `${item.successRows} succeeded · ${item.failureRows} failed`
                    : `${item.status} · ${item.progressPercent}%`}</span>
                  <span>{item.uploadedAt ? new Date(item.uploadedAt).toLocaleString() : "Time unavailable"}</span>
                </summary>
                <div className="mt-2 space-y-1 border-t border-[#e4e6de] pt-2 text-xs text-[#687064]">
                  <p>Updated by {item.uploadedBy || item.uploadedById} · {item.fileSize?.toLocaleString()} bytes</p>
                  <p>{item.processedRows} / {item.totalRows} rows processed · {item.successRows} succeeded · {item.failureRows} failed</p>
                  {item.sha256 && <p className="break-all font-mono text-[10px]">SHA-256: {item.sha256}</p>}
                  {item.failureReason && <p className="text-red-700">Error: {item.failureReason}</p>}
                  {(item.changes || []).map((change, index) => (
                    <p key={`${change.productCode}-${change.color}-${index}`}>
                      {change.productCode} · {change.productName} · {change.category} / {change.productType} · {change.color}:
                      {" "}{change.previousStockCount} → {change.stockCount}
                    </p>
                  ))}
                  {(item.failures || []).map((failure, index) => (
                    <p key={`failure-${failure.rowNumber}-${index}`} className="text-red-700">
                      Row {failure.rowNumber}: {failure.productCode} · {failure.color} — {failure.message}
                    </p>
                  ))}
                </div>
              </details>
            ))}
            {historyPages > 1 && <div className="flex items-center justify-between pt-2">
              <button type="button" disabled={historyPage === 0} onClick={() => setHistoryPage((current) => current - 1)}
                className="border border-[#dfe2d9] px-3 py-1.5 text-xs text-[#52644f] disabled:opacity-40">Previous</button>
              <span className="text-xs text-[#687064]">Page {historyPage + 1} of {historyPages}</span>
              <button type="button" disabled={historyPage + 1 >= historyPages} onClick={() => setHistoryPage((current) => current + 1)}
                className="border border-[#dfe2d9] px-3 py-1.5 text-xs text-[#52644f] disabled:opacity-40">Next</button>
            </div>}
          </div>
        )}
      </div>
    </section>
  );
}
