export default function LoadingState({ label = "Loading…", compact = false }) {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy="true"
      className={`flex items-center justify-center gap-3 text-[#586557] ${compact ? "py-5" : "min-h-[40vh] py-12"}`}
    >
      <span className="relative grid h-10 w-10 shrink-0 place-items-center">
        <span className="absolute inset-0 animate-spin rounded-full border-2 border-[#e2e7de] border-t-[#314638]" />
        <span className="grid h-6 w-6 place-items-center rounded-full bg-[#314638] text-[8px] font-semibold tracking-tight text-white">F/F</span>
      </span>
      <span className="text-sm">{label}</span>
    </div>
  );
}
