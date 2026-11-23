import { useState } from "react";
import AddressBook from "../../components/address/AddressBook";

export default function AddressesPage() {
  const [selectedId, setSelectedId] = useState("");

  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#788174]">My account</p>
      <h1 className="mt-2 mb-7 text-3xl font-normal tracking-[-0.04em] text-[#29382d]">Saved addresses</h1>
      <AddressBook selectedId={selectedId} onSelect={setSelectedId} selectable={false} />
    </div>
  );
}
