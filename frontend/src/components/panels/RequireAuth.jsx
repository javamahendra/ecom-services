import { Navigate, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";

export default function RequireAuth({ role, children }) {
  const user = useSelector((state) => state.auth);
  const location = useLocation();

  if (!user?.token) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (role && !user.roles?.includes(role)) {
    const destination = user.roles?.includes("admin") ? "/admin" : user.roles?.includes("manager") ? "/manager" : "/account";
    return <Navigate to={destination} replace />;
  }
  return children;
}
