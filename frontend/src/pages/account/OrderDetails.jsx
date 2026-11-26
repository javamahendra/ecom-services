import { ArrowLeft, Package } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { normalizeProduct } from "../../api";
import { getProduct } from "../../services/productService";
import { getOrder } from "../../services/orderService";
import { productImage } from "../../components/ProductCard";
import LoadingState from "../../components/LoadingState";
import { isOrderProcessing, orderStatusLabel } from "../../utils/orderStatus";

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

function OrderItemDetails({ item }) {
  const [image, setImage] = useState(item.imageUrl || item.image || "");

  useEffect(() => {
    let active = true;
    if (image || !item.productId) return () => { active = false; };
    getProduct(item.productId)
      .then((product) => {
        if (active) setImage(productImage(normalizeProduct(product)));
      })
      .catch(() => {});
    return () => { active = false; };
  }, [image, item.productId]);

  return (
    <div className="flex min-w-0 flex-1 items-start gap-4">
      <Link to={`/products/${encodeURIComponent(item.productId)}`} aria-label={`View ${item.productName} details`} className="h-24 w-20 shrink-0 overflow-hidden bg-[#eeefe9] sm:h-28 sm:w-24">
        <img src={image || productImage({})} alt={item.productName} className="h-full w-full object-cover transition duration-300 hover:scale-105" />
      </Link>
      <div className="min-w-0">
        <Link to={`/products/${encodeURIComponent(item.productId)}`} className="text-sm font-medium text-[#354237] hover:text-[#52644f] hover:underline">{item.productName}</Link>
        <p className="mt-1 text-xs text-[#858a80]">{item.color} · Qty {item.quantity}</p>
        <Link to={`/products/${encodeURIComponent(item.productId)}`} className="mt-2 inline-block text-xs text-[#52644f] hover:underline">View product details</Link>
      </div>
    </div>
  );
}

export default function OrderDetailsPage() {
  const { orderId } = useParams();
  const token = useSelector((state) => state.auth?.token);
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    getOrder(orderId, token)
      .then((result) => {
        if (active) setOrder(result);
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load order details.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [orderId, token]);

  useEffect(() => {
    if (!order || !isOrderProcessing(order.status)) return undefined;
    let active = true;
    let hasReportedError = false;
    const timer = window.setInterval(() => {
      getOrder(orderId, token)
        .then((result) => {
          if (active) setOrder(result);
        })
        .catch((error) => {
          if (active && !hasReportedError) {
            hasReportedError = true;
            toast.error(error.message || "Could not refresh order status.");
          }
        });
    }, 5000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [order, orderId, token]);

  if (loading) return <LoadingState label="Loading order details…" />;

  if (!order) {
    return (
      <div>
        <p className="text-sm text-[#687064]">This order could not be found.</p>
        <Link to="/account/orders" className="mt-4 inline-flex items-center gap-2 text-xs text-[#52644f]"><ArrowLeft size={14} /> Back to my orders</Link>
      </div>
    );
  }

  const address = order.shippingAddress;
  const items = order.items || [];

  return (
    <div>
      <Link to="/account/orders" className="inline-flex items-center gap-2 text-xs text-[#777e73] hover:text-[#314638]"><ArrowLeft size={14} /> Back to my orders</Link>
      <div className="mt-5 flex flex-wrap items-end justify-between gap-4 border-b border-[#e1e4dc] pb-6">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#788174]">Order details</p>
          <h1 className="mt-2 text-3xl font-normal tracking-[-0.04em] text-[#29382d]">Order #{order.id?.slice(-8)}</h1>
          <p className="mt-2 text-xs text-[#858a80]">Placed {formatDate(order.createdAt)}</p>
        </div>
        <span className="rounded-full bg-[#e9ece5] px-3 py-1.5 text-xs font-medium text-[#52644f]">{orderStatusLabel(order.status)}</span>
      </div>
      {order.status === "PENDING_INVENTORY" && (
        <p className="mt-4 border border-[#e4e6de] bg-[#fbfbf8] px-4 py-3 text-xs leading-5 text-[#687064]">
          Your order is placed. Inventory is being checked automatically; this status will update when the check completes.
        </p>
      )}
      {order.status === "CANCELLATION_PENDING" && (
        <p className="mt-4 border border-[#e4e6de] bg-[#fbfbf8] px-4 py-3 text-xs leading-5 text-[#687064]">
          Your cancellation is being processed and this status will update automatically.
        </p>
      )}

      <div className="mt-6 grid gap-5 lg:grid-cols-[1fr_300px]">
        <section>
          <div className="flex items-center gap-2"><Package size={17} className="text-[#52644f]" /><h2 className="text-lg font-medium text-[#354237]">Items</h2></div>
          <div className="mt-3 border border-[#e4e6de] bg-white">
            {items.map((item, index) => (
              <div key={`${item.productId}-${item.variantId}-${index}`} className="flex flex-wrap items-start justify-between gap-4 border-b border-[#eef0ea] p-4 last:border-0">
                <OrderItemDetails item={item} />
                <div className="min-w-36 text-sm text-[#586257] sm:text-right">
                  <p>Unit price: ${Number(item.unitPrice || 0).toFixed(2)}</p>
                  <p className="mt-1 font-medium text-[#354237]">Line total: ${(Number(item.unitPrice || 0) * item.quantity).toFixed(2)}</p>
                </div>
              </div>
            ))}
            {items.length === 0 && <p className="p-4 text-sm text-[#858a80]">No item details available.</p>}
          </div>
        </section>

        <aside className="space-y-4">
          <section className="border border-[#e4e6de] bg-white p-5">
            <h2 className="text-sm font-medium text-[#354237]">Delivery address</h2>
            {address ? (
              <div className="mt-3 text-xs leading-5 text-[#687064]">
                <p className="font-medium text-[#354237]">{address.recipientName}</p>
                <p>{address.phone}</p>
                <p className="mt-2">{[address.line1, address.line2, address.city]
                  .filter((part, index, parts) => part && parts.findIndex(
                    (candidate) => candidate?.trim().toLowerCase() === part.trim().toLowerCase(),
                  ) === index)
                  .join(", ")}</p>
                <p>{address.region} {address.postalCode}</p>
                <p>{address.country}</p>
              </div>
            ) : <p className="mt-3 text-xs text-[#858a80]">No address details available.</p>}
          </section>
          <section className="border border-[#e4e6de] bg-white p-5">
            <h2 className="text-sm font-medium text-[#354237]">Payment summary</h2>
            <div className="mt-4 flex justify-between text-sm text-[#687064]"><span>Total</span><span className="font-medium text-[#354237]">${Number(order.total || 0).toFixed(2)}</span></div>
            <p className="mt-2 text-[11px] text-[#92978e]">Updated {formatDate(order.updatedAt)}</p>
          </section>
        </aside>
      </div>
    </div>
  );
}
