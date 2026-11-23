import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { logoutUser } from "../../api";
import {
  clearSessionActivity,
  getLastActivity,
  markSessionActivity,
  SESSION_IDLE_TIMEOUT_MS,
} from "../../auth/sessionActivity";
import { clearUser } from "../../store/slices/authSlice";

export default function SessionActivityMonitor() {
  const user = useSelector((state) => state.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!user?.token) return undefined;

    let timer;
    let lastPointerActivity = 0;
    const redirectToLogin = () => {
      const refreshToken = user.refreshToken;
      dispatch(clearUser());
      clearSessionActivity();
      navigate("/login", {
        replace: true,
        state: { from: `${location.pathname}${location.search}${location.hash}` },
      });
      toast.info("Your session ended because of inactivity. Please sign in again.");
      if (refreshToken) {
        logoutUser(refreshToken).catch((error) => {
          toast.error(error.message || "Could not revoke your expired session.");
        });
      }
    };

    const scheduleIdleCheck = () => {
      window.clearTimeout(timer);
      const lastActivity = getLastActivity();
      if (!lastActivity) {
        markSessionActivity();
        timer = window.setTimeout(redirectToLogin, SESSION_IDLE_TIMEOUT_MS);
        return;
      }

      const remaining = SESSION_IDLE_TIMEOUT_MS - (Date.now() - lastActivity);
      if (remaining <= 0) {
        redirectToLogin();
        return;
      }
      timer = window.setTimeout(redirectToLogin, remaining);
    };

    const recordActivity = (event) => {
      if (event.type === "pointermove") {
        const now = Date.now();
        if (now - lastPointerActivity < 1000) return;
        lastPointerActivity = now;
      }
      markSessionActivity();
      scheduleIdleCheck();
    };

    const checkVisibility = () => {
      if (document.visibilityState === "visible") scheduleIdleCheck();
    };

    scheduleIdleCheck();
    const activityEvents = ["keydown", "pointerdown", "pointermove", "scroll", "touchstart"];
    activityEvents.forEach((event) => window.addEventListener(event, recordActivity, { passive: true }));
    document.addEventListener("visibilitychange", checkVisibility);

    return () => {
      window.clearTimeout(timer);
      activityEvents.forEach((event) => window.removeEventListener(event, recordActivity));
      document.removeEventListener("visibilitychange", checkVisibility);
    };
  }, [dispatch, location.hash, location.pathname, location.search, navigate, user?.refreshToken, user?.token]);

  return null;
}
