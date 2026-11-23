import { ArrowLeft, Heart, LayoutDashboard, LogOut, MapPin, Package, Store } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { clearUser } from "../../store/slices/authSlice";
import { logoutUser } from "../../api";
import { toast } from "react-toastify";
import { clearSessionActivity } from "../../auth/sessionActivity";

export default function PanelLayout({ panel }) {
  const user = useSelector((state) => state.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const isAdmin = panel === "admin";
  const isManager = panel === "manager";
  const isStaff = isAdmin || isManager;
  const home = isAdmin ? "/admin" : isManager ? "/manager" : "/account";

  const signOut = () => {
    const refreshToken = user?.refreshToken;
    dispatch(clearUser());
    clearSessionActivity();
    navigate("/");
    if (refreshToken) {
      logoutUser(refreshToken).catch((error) => {
        toast.error(error.message || "Could not revoke your session.");
      });
    }
  };

  return (
    <div className="min-h-[75vh] bg-[#f7f7f3]">
      <header className="border-b border-[#e4e6de] bg-white">
        <div className="mx-auto flex min-h-16 max-w-[1440px] items-center justify-between gap-4 px-5 sm:px-8 lg:px-12">
          <div className="flex items-center gap-3">
            <NavLink to="/" className="grid h-9 w-9 place-items-center rounded-full bg-[#314638] text-xs font-semibold text-white" aria-label="Go to storefront">F/F</NavLink>
            <div>
              <p className="text-sm font-medium text-[#354237]">{isAdmin ? "Admin panel" : isManager ? "Manager panel" : "Your account"}</p>
              <p className="text-[11px] text-[#858a80]">{user?.email || user?.name}</p>
            </div>
          </div>
          <div className="flex items-center gap-2 sm:gap-4">
            <NavLink to="/" className="inline-flex items-center gap-1.5 px-2 py-2 text-xs text-[#687064] hover:text-[#314638]"><Store size={15} /> <span className="hidden sm:inline">Storefront</span></NavLink>
            <button onClick={signOut} className="inline-flex items-center gap-1.5 px-2 py-2 text-xs text-[#687064] hover:text-[#8b4d43]"><LogOut size={15} /> Sign out</button>
          </div>
        </div>
      </header>
      <div className="mx-auto grid max-w-[1440px] gap-8 px-5 py-8 sm:px-8 lg:grid-cols-[220px_1fr] lg:px-12 lg:py-12">
        <aside>
          <p className="mb-3 px-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-[#92978e]">{isStaff ? "Catalog management" : "My account"}</p>
          <nav className="flex gap-2 overflow-x-auto lg:flex-col">
            <NavLink end to={home} className={({ isActive }) => `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${isActive ? "bg-[#e9ece5] font-medium text-[#314638]" : "text-[#687064] hover:bg-[#eeefe9]"}`}>
              <LayoutDashboard size={16} /> Overview
            </NavLink>
            {!isStaff && <NavLink to="/account/orders" className={({ isActive }) => `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${isActive ? "bg-[#e9ece5] font-medium text-[#314638]" : "text-[#687064] hover:bg-[#eeefe9]"}`}>
              <Package size={16} /> My orders
            </NavLink>}
            {!isStaff && <NavLink to="/account/wishlist" className={({ isActive }) => `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${isActive ? "bg-[#e9ece5] font-medium text-[#314638]" : "text-[#687064] hover:bg-[#eeefe9]"}`}>
              <Heart size={16} /> Wishlist
            </NavLink>}
            {!isStaff && <NavLink to="/account/addresses" className={({ isActive }) => `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${isActive ? "bg-[#e9ece5] font-medium text-[#314638]" : "text-[#687064] hover:bg-[#eeefe9]"}`}>
              <MapPin size={16} /> Addresses
            </NavLink>}
            <NavLink to="/" className="flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-[#687064] hover:bg-[#eeefe9]"><ArrowLeft size={16} /> Back to store</NavLink>
          </nav>
        </aside>
        <section className="min-w-0"><Outlet /></section>
      </div>
    </div>
  );
}
