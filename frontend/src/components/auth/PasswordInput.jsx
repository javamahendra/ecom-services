import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

export default function PasswordInput({ registration, autoComplete, minLength, error }) {
  const [visible, setVisible] = useState(false);

  return (
    <label className="block text-xs text-[#5d655a]">
      Password
      <span className="relative mt-2 block">
        <input
          type={visible ? "text" : "password"}
          autoComplete={autoComplete}
          {...registration}
          className="h-11 w-full border border-[#dfe2d9] bg-white px-3 pr-11 text-sm outline-none transition focus:border-[#71816d]"
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          className="absolute right-0 top-0 grid h-11 w-11 place-items-center text-[#8b9187]"
          aria-label={visible ? "Hide password" : "Show password"}
        >
          {visible ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </span>
      {minLength && <span className="mt-1.5 block text-[11px] text-[#92978e]">Use at least {minLength} characters.</span>}
      {error && <span className="mt-1 block text-xs text-red-700">{error}</span>}
    </label>
  );
}
