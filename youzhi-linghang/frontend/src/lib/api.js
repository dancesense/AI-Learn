const TOKEN_KEY = "youzhi_auth_token";

function authToken() {
  return window.localStorage.getItem(TOKEN_KEY);
}

async function request(path, options = {}) {
  const token = authToken();
  const response = await fetch(path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    let message = "请求失败，请稍后重试";
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      // The generic message is sufficient for non-JSON failures.
    }
    throw new Error(message);
  }
  return response.json();
}

export const api = {
  hasSession: () => Boolean(authToken()),
  saveSession: (response) => {
    window.localStorage.setItem(TOKEN_KEY, response.token);
    return response.user;
  },
  clearSession: () => window.localStorage.removeItem(TOKEN_KEY),
  login: (payload) => request("/api/auth/login", { method: "POST", body: JSON.stringify(payload) }),
  register: (payload) =>
    request("/api/auth/register", { method: "POST", body: JSON.stringify(payload) }),
  me: () => request("/api/auth/me"),
  logout: () => request("/api/auth/logout", { method: "POST" }),
  getHome: () => request("/api/home"),
  getContents: ({ category = "全部", q = "" } = {}) =>
    request(`/api/contents?category=${encodeURIComponent(category)}&q=${encodeURIComponent(q)}`),
  likeContent: (id) => request(`/api/contents/${id}/like`, { method: "POST" }),
  followCreator: (creatorName) =>
    request("/api/follows", { method: "POST", body: JSON.stringify({ creatorName }) }),
  getTutors: ({ subject = "全部", grade = "全部", priceRange = "全部", q = "" } = {}) =>
    request(
      `/api/tutors?subject=${encodeURIComponent(subject)}&grade=${encodeURIComponent(grade)}&priceRange=${encodeURIComponent(priceRange)}&q=${encodeURIComponent(q)}`,
    ),
  createReservation: (payload) =>
    request("/api/reservations", { method: "POST", body: JSON.stringify(payload) }),
  getCommunities: () => request("/api/communities"),
  joinCommunity: (id) => request(`/api/communities/${id}/join`, { method: "POST" }),
  getProfile: () => request("/api/profile"),
  updateRole: (role) =>
    request("/api/profile/role", { method: "PUT", body: JSON.stringify({ role }) }),
  getReservations: () => request("/api/profile/reservations"),
};
