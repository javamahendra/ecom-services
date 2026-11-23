import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

export default function AuthShell({ eyebrow, title, description, children }) {
  return (
    <main className="mx-auto grid min-h-[70vh] max-w-[1180px] px-5 py-10 sm:px-8 lg:grid-cols-2 lg:gap-16 lg:px-12 lg:py-16">
      <div className="hidden min-h-[600px] flex-col justify-between bg-[#e8e9df] p-12 lg:flex">
        <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-[#657262]">Form & Field</p>
        <div>
          <p className="text-5xl font-normal leading-[1.08] tracking-[-0.05em] text-[#29382d]">
            A thoughtful<br /><em className="font-serif font-light">kind of everyday.</em>
          </p>
          <p className="mt-5 max-w-xs text-sm leading-7 text-[#6e766a]">Good things are better when they find their way home.</p>
        </div>
        <span className="text-xs text-[#7b8376]">Useful. Beautiful. Made to last.</span>
      </div>
      <div className="mx-auto flex w-full max-w-[460px] flex-col justify-center py-8 lg:py-4">
        <Link to="/" className="mb-10 inline-flex items-center gap-2 text-xs text-[#777e73] hover:text-[#314638]">
          <ArrowLeft size={14} /> Back to the store
        </Link>
        <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-[#788174]">{eyebrow}</p>
        <h1 className="mt-3 text-4xl font-normal tracking-[-0.05em] text-[#29382d]">{title}</h1>
        <p className="mt-3 text-sm text-[#81877c]">{description}</p>
        {children}
      </div>
    </main>
  );
}
