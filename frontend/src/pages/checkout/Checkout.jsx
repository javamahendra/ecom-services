import { ArrowLeft, ArrowRight, LockKeyhole } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { toast } from "react-toastify";
import AddressBook from "../../components/address/AddressBook";
import { request } from "../../api";
import { createOrder } from "../../services/orderService";
import { clearCart } from "../../store/slices/cartSlice";

export default function CheckoutPage() {
  const items = useSelector((state) => state.cart);
  const user = useSelector((state) => state.auth);
  const [selectedAddressId, setSelectedAddressId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const subtotal = items.reduce((sum, item) => sum + item.price * item.quantity, 0);

  const placeOrder = async () => {
    if (items.length === 0) {
      toast.error("Your bag is empty.");
      return;
    }
    if (!selectedAddressId) {
      toast.error("Choose a delivery address before placing your order.");
      return;
    }
    const addresses = await request("/api/addresses", { token: user.token }).catch((error) => {
      toast.error(error.message || "Could not verify the selected address.");
      return null;
    });
    if (!addresses) return;
    const address = addresses.find((entry) => entry.id === selectedAddressId);
    if (!address) {
      toast.error("That delivery address is no longer available. Please select another.");
      setSelectedAddressId("");
      return;
    }

    setSubmitting(true);
    try {
      const order = await createOrder({
          email: user.email || undefined,
          items: items.map((item) => ({
            productId: item.productId,
            variantId: item.variantId || "default",
            productName: item.name,
            imageUrl: item.image || "",
            color: item.color || "Standard",
            quantity: item.quantity,
            unitPrice: item.price,
          })),
          shippingAddress: {
            recipientName: address.recipientName,
            phone: address.phone,
            line1: address.line1,
            line2: address.line2 || "",
            city: address.city,
            region: address.region,
            postalCode: address.postalCode,
            country: address.country,
          },
        }, user.token);
      dispatch(clearCart());
      toast.success("Your order has been placed.");
      navigate("/account/orders", { replace: true, state: { createdOrderId: order?.id } });
    } catch (error) {
      toast.error(error.message || "Could not place your order.");
    } finally {
      setSubmitting(false);
    }
  };

  if (items.length === 0) {
    return (
      <main className="mx-auto min-h-[50vh] max-w-3xl px-5 py-24 text-center">
        <h1 className="text-3xl font-normal text-[#29382d]">Your bag is empty.</h1>
        <Link to="/" className="mt-6 inline-flex items-center gap-2 text-sm text-[#52644f]"><ArrowLeft size={15} /> Continue shopping</Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-[1180px] px-5 py-10 sm:px-8 lg:px-12 lg:py-14">
      <Link to="/cart" className="inline-flex items-center gap-2 text-xs text-[#777e73] hover:text-[#314638]"><ArrowLeft size={14} /> Back to your bag</Link>
      <div className="mt-5 border-b border-[#e1e4dc] pb-6">
        <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-[#788174]">Almost yours</p>
        <h1 className="mt-2 text-4xl font-normal tracking-[-0.05em] text-[#29382d]">Checkout</h1>
      </div>
      <div className="grid gap-10 pt-8 lg:grid-cols-[1fr_340px]">
        <section>
          <AddressBook selectedId={selectedAddressId} onSelect={setSelectedAddressId} />
        </section>
        <aside className="h-fit border border-[#e4e6de] bg-white p-6 sm:p-8">
          <h2 className="text-lg font-medium text-[#354237]">Order summary</h2>
          <div className="mt-5 max-h-64 space-y-4 overflow-y-auto">
            {items.map((item) => (
              <div key={item.id} className="flex justify-between gap-3 text-sm">
                <div className="min-w-0"><p className="truncate text-[#586257]">{item.name}</p><p className="mt-1 text-xs text-[#92978e]">Qty {item.quantity}</p></div>
                <span className="shrink-0 text-[#354237]">${(item.price * item.quantity).toFixed(2)}</span>
              </div>
            ))}
          </div>
          <div className="my-5 h-px bg-[#e4e6de]" />
          <div className="flex justify-between text-sm font-medium text-[#354237]"><span>Total</span><span>${subtotal.toFixed(2)}</span></div>
          <p className="mt-2 text-xs leading-5 text-[#858a80]">Shipping and taxes are not included in this total.</p>
          <button onClick={placeOrder} disabled={submitting || !selectedAddressId} className="mt-6 flex h-12 w-full items-center justify-center gap-2 bg-[#314638] text-xs font-medium uppercase tracking-[0.12em] text-white transition hover:bg-[#25372b] disabled:cursor-not-allowed disabled:opacity-50">
            {submitting ? "Placing order…" : "Place order"} {!submitting && <ArrowRight size={14} />}
          </button>
          <p className="mt-4 flex items-center justify-center gap-1.5 text-[11px] text-[#858a80]"><LockKeyhole size={12} /> Your order is securely submitted.</p>
        </aside>
      </div>
    </main>
  );
}
