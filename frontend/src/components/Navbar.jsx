import { Heart, LayoutDashboard, Package, ShoppingBag, UserRound } from "lucide-react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { clearUser } from "../store/slices/authSlice";
import { logoutUser } from "../api";
import { toast } from "react-toastify";
import { clearSessionActivity } from "../auth/sessionActivity";

export default function Navbar() {
  const cart = useSelector((state) => state.cart);
  const user = useSelector((state) => state.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const count = cart.reduce((total, item) => total + item.quantity, 0);
  const isAdmin = user?.roles?.includes("admin");
  const isManager = user?.roles?.includes("manager") && !isAdmin;

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
    <header className="sticky top-0 z-30 border-b border-[#e7e8e1] bg-[#f7f7f3]/95 backdrop-blur">
      <div className="mx-auto flex h-[76px] max-w-[1440px] items-center justify-between px-5 sm:px-8 lg:px-12">
        <Link to="/" className="group flex items-center gap-2.5" aria-label="Form and Field home">
          <span className="grid h-9 w-9 place-items-center rounded-full bg-[#314638] text-xs font-semibold tracking-tight text-white">F/F</span>
          <span className="text-[15px] font-semibold tracking-[0.12em]">FORM <span className="font-normal text-[#9a9d92]">&</span> FIELD</span>
        </Link>
        <nav className="hidden items-center gap-9 text-[13px] text-[#666b61] md:flex">
          <NavLink to="/" className={({ isActive }) => isActive ? "text-[#263d2d]" : "hover:text-[#263d2d"}>Shop all</NavLink>
          <a href="/#story" className="hover:text-[#263d2d]">Our approach</a>
        </nav>
        <div className="flex items-center gap-2 sm:gap-4">
          {user && !isAdmin && !isManager && (
            <NavLink
              to="/account/orders"
              className={({ isActive }) => `inline-flex items-center gap-1.5 rounded-full px-2 py-2 text-sm transition hover:bg-[#eeefe9] ${isActive ? "text-[#263d2d]" : "text-[#454b43]"}`}
            >
              <Package size={16} strokeWidth={1.7} /><span>Orders</span>
            </NavLink>
          )}
          {user && !isAdmin && !isManager && (
            <NavLink
              to="/account/wishlist"
              aria-label="Wishlist"
              className={({ isActive }) => `inline-flex items-center gap-1.5 rounded-full px-2 py-2 text-sm transition hover:bg-[#eeefe9] ${isActive ? "text-[#263d2d]" : "text-[#454b43]"}`}
            >
              <Heart size={16} strokeWidth={1.7} /><span>Wishlist</span>
            </NavLink>
          )}
          {user ? (
            <div className="group relative">
              <button className="flex items-center gap-2 rounded-full px-2 py-2 text-sm text-[#3d443d] hover:bg-[#eeefe9]" aria-label="Account options">
                <span className="grid h-8 w-8 place-items-center rounded-full bg-[#e6e9df] text-[#344b3b]"><UserRound size={16} /></span>
                <span className="hidden max-w-32 truncate text-left sm:block">{user.name || user.email}</span>
              </button>
              <div className="invisible absolute right-0 top-full mt-1 w-48 rounded-xl border border-[#e7e8e1] bg-white p-2 opacity-0 shadow-lg transition group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100">
                <div className="truncate px-3 py-2 text-xs text-[#8a8f84]">{user.email}</div>
                <Link to={isAdmin ? "/admin" : isManager ? "/manager" : "/account"} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm hover:bg-[#f4f5f0]">
                  <LayoutDashboard size={14} /> {isAdmin ? "Admin panel" : isManager ? "Manager panel" : "My account"}
                </Link>
                <button onClick={signOut} className="w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-[#f4f5f0]">Sign out</button>
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-3">
              <Link to="/login" className="flex items-center gap-2 px-2 py-2 text-sm text-[#454b43] hover:text-[#263d2d]">
                <UserRound size={17} strokeWidth={1.7} /><span className="hidden sm:inline">Sign in</span>
              </Link>
            </div>
          )}
          <Link to="/cart" className="relative flex h-10 items-center gap-2 rounded-full bg-[#314638] px-4 text-sm text-white transition hover:bg-[#25372b]" aria-label={`Shopping bag, ${count} items`}>
            <ShoppingBag size={16} strokeWidth={1.7} />
            <span className="hidden sm:inline">Bag</span>
            <span className="grid h-5 min-w-5 place-items-center rounded-full bg-white/15 px-1 text-[11px]">{count}</span>
          </Link>
        </div>
      </div>
    </header>
  );
}
