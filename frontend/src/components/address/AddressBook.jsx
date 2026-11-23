import { zodResolver } from "@hookform/resolvers/zod";
import { Check, Edit3, MapPin, Plus, Star, Trash2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { z } from "zod";
import { request } from "../../api";
import LoadingState from "../LoadingState";

const addressSchema = z.object({
  label: z.string().trim().min(1, "Enter a label such as Home or Work.").max(60),
  recipientName: z.string().trim().min(1, "Enter the recipient name.").max(150),
  phone: z.string().trim().min(7, "Enter a valid phone number.").max(30),
  line1: z.string().trim().min(1, "Enter the street address.").max(200),
  line2: z.string().max(200),
  city: z.string().trim().min(1, "Enter a city.").max(100),
  region: z.string().trim().min(1, "Enter a state or region.").max(100),
  postalCode: z.string().trim().min(1, "Enter a postal code.").max(20),
  country: z.string().trim().min(1, "Enter a country.").max(100),
  defaultAddress: z.boolean(),
});

const inputClass = "mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d]";
const fieldClass = "block text-xs text-[#5d655a]";

function addressPayload(address, defaultAddress = false) {
  return {
    label: address.label,
    recipientName: address.recipientName,
    phone: address.phone,
    line1: address.line1,
    line2: address.line2 || "",
    city: address.city,
    region: address.region,
    postalCode: address.postalCode,
    country: address.country,
    defaultAddress,
  };
}

export default function AddressBook({ selectedId, onSelect, selectable = true }) {
  const user = useSelector((state) => state.auth);
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState(null);
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(addressSchema),
    defaultValues: {
      label: "",
      recipientName: "",
      phone: "",
      line1: "",
      line2: "",
      city: "",
      region: "",
      postalCode: "",
      country: "",
      defaultAddress: false,
    },
  });

  const loadAddresses = useCallback(async () => {
    try {
      const result = await request("/api/addresses", { token: user.token });
      const list = Array.isArray(result) ? result : [];
      setAddresses(list);
      if (!selectedIdRef.current || !list.some((address) => address.id === selectedIdRef.current)) {
        onSelect(list.find((address) => address.defaultAddress)?.id || list[0]?.id || "");
      }
    } catch (error) {
      toast.error(error.message || "Could not load your saved addresses.");
    } finally {
      setLoading(false);
    }
  }, [onSelect, user.token]);

  useEffect(() => {
    loadAddresses();
  }, [loadAddresses]);

  const saveAddress = async (values) => {
    try {
      const editing = Boolean(editingAddressId);
      const saved = await request(editing
        ? `/api/addresses/${encodeURIComponent(editingAddressId)}`
        : "/api/addresses", {
        method: editing ? "PUT" : "POST",
        token: user.token,
        body: addressPayload(values, values.defaultAddress || addresses.length === 0),
      });
      toast.success(editing ? "Address updated." : "Address saved.");
      reset();
      setShowForm(false);
      setEditingAddressId(null);
      await loadAddresses();
      if (selectable) onSelect(saved.id);
    } catch (error) {
      toast.error(error.message || (editingAddressId ? "Could not update your address." : "Could not save your address."));
    }
  };

  const editAddress = (address) => {
    setEditingAddressId(address.id);
    reset({
      label: address.label || "",
      recipientName: address.recipientName || "",
      phone: address.phone || "",
      line1: address.line1 || "",
      line2: address.line2 || "",
      city: address.city || "",
      region: address.region || "",
      postalCode: address.postalCode || "",
      country: address.country || "",
      defaultAddress: Boolean(address.defaultAddress),
    });
    setShowForm(true);
  };

  const cancelForm = () => {
    setShowForm(false);
    setEditingAddressId(null);
    reset();
  };

  const makeDefault = async (address) => {
    try {
      await request(`/api/addresses/${encodeURIComponent(address.id)}`, {
        method: "PUT",
        token: user.token,
        body: addressPayload(address, true),
      });
      toast.success("Main address updated.");
      await loadAddresses();
      onSelect(address.id);
    } catch (error) {
      toast.error(error.message || "Could not update your main address.");
    }
  };

  const deleteAddress = async (address) => {
    if (!window.confirm(`Remove the ${address.label} address?`)) return;
    try {
      await request(`/api/addresses/${encodeURIComponent(address.id)}`, {
        method: "DELETE",
        token: user.token,
      });
      toast.success("Address removed.");
      if (selectedId === address.id) onSelect("");
      await loadAddresses();
    } catch (error) {
      toast.error(error.message || "Could not remove your address.");
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-medium text-[#354237]">Delivery addresses</h2>
          <p className="mt-1 text-xs text-[#858a80]">Choose a delivery address or manage your saved addresses.</p>
        </div>
        {!showForm && <button type="button" onClick={() => { reset(); setEditingAddressId(null); setShowForm(true); }} className="inline-flex shrink-0 items-center gap-1.5 border border-[#dfe2d9] bg-white px-3 py-2 text-xs text-[#52644f] hover:border-[#71816d]"><Plus size={14} /> Add address</button>}
      </div>

      {loading ? <LoadingState label="Loading addresses…" compact /> : addresses.length === 0 && !showForm ? (
        <div className="mt-4 border border-dashed border-[#d5d9cf] bg-white px-5 py-9 text-center">
          <MapPin size={20} className="mx-auto text-[#8a9385]" />
          <p className="mt-3 text-sm text-[#687064]">You haven’t saved an address yet.</p>
          <button type="button" onClick={() => { reset(); setEditingAddressId(null); setShowForm(true); }} className="mt-3 text-xs font-medium text-[#52644f] underline underline-offset-4">Add your first address</button>
        </div>
      ) : (
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          {addresses.map((address) => (
            <article key={address.id} className={`border bg-white p-4 ${selectedId === address.id ? "border-[#526b53] ring-1 ring-[#526b53]" : "border-[#e4e6de]"}`}>
              <div className="flex items-start gap-3">
                {selectable && <input type="radio" name="deliveryAddress" value={address.id} checked={selectedId === address.id} onChange={() => onSelect(address.id)} aria-label={`Deliver to ${address.label}`} className="mt-1 accent-[#314638]" />}
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-sm font-medium text-[#354237]">{address.label}</h3>
                    {address.defaultAddress && <span className="inline-flex items-center gap-1 rounded-full bg-[#e9ece5] px-2 py-0.5 text-[10px] text-[#52644f]"><Star size={10} /> Main</span>}
                    {selectedId === address.id && selectable && <Check size={14} className="ml-auto text-[#52644f]" />}
                  </div>
                  <p className="mt-2 text-sm text-[#586257]">{address.recipientName}</p>
                  <p className="mt-1 text-xs leading-5 text-[#858a80]">{address.line1}{address.line2 ? `, ${address.line2}` : ""}<br />{address.city}, {address.region} {address.postalCode}<br />{address.country}</p>
                  <p className="mt-1 text-xs text-[#858a80]">{address.phone}</p>
                  <div className="mt-3 flex flex-wrap gap-3 border-t border-[#eef0ea] pt-3">
                    <button type="button" onClick={() => editAddress(address)} className="inline-flex items-center gap-1 text-xs text-[#52644f] hover:underline"><Edit3 size={12} /> Edit</button>
                    {!address.defaultAddress && <button type="button" onClick={() => makeDefault(address)} className="text-xs text-[#52644f] hover:underline">Make main</button>}
                    <button type="button" onClick={() => deleteAddress(address)} className="inline-flex items-center gap-1 text-xs text-[#92978e] hover:text-[#8b4d43]"><Trash2 size={12} /> Remove</button>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit(saveAddress)} noValidate className="mt-5 border border-[#e4e6de] bg-white p-5 sm:p-6">
          <div className="flex items-start justify-between gap-3">
            <div><h3 className="text-sm font-medium text-[#354237]">{editingAddressId ? "Edit address" : "Add a new address"}</h3><p className="mt-1 text-xs text-[#858a80]">{editingAddressId ? "Update the saved delivery details." : "Save another place for your deliveries."}</p></div>
            <button type="button" onClick={cancelForm} className="text-xs text-[#858a80] hover:text-[#354237]">Cancel</button>
          </div>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            {[
              ["label", "Address label"], ["recipientName", "Recipient name"], ["phone", "Phone number"],
              ["line1", "Address line 1"], ["line2", "Address line 2 (optional)"], ["city", "City"],
              ["region", "State / region"], ["postalCode", "Postal code"], ["country", "Country"],
            ].map(([name, label]) => (
              <label key={name} className={`${fieldClass} ${name === "line1" ? "sm:col-span-2" : ""}`}>
                {label}
                <input {...register(name)} autoComplete={name === "recipientName" ? "name" : name === "phone" ? "tel" : undefined} className={inputClass} />
                {errors[name] && <span className="mt-1 block text-xs text-red-700">{errors[name].message}</span>}
              </label>
            ))}
          </div>
          <label className="mt-4 flex items-center gap-2 text-xs text-[#5d655a]">
            <input type="checkbox" {...register("defaultAddress")} className="accent-[#314638]" />
            Make this my main address
          </label>
          <button disabled={isSubmitting} className="mt-5 h-10 bg-[#314638] px-5 text-xs font-medium uppercase tracking-[0.1em] text-white hover:bg-[#25372b] disabled:opacity-60">{isSubmitting ? "Saving…" : editingAddressId ? "Update address" : "Save address"}</button>
        </form>
      )}
    </div>
  );
}
