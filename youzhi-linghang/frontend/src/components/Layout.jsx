import { LogOut } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import { AuthModal } from "./AuthModal";
import { Toast } from "./Ui";

const navItems = [
  ["首页", "/home"],
  ["内容广场", "/content"],
  ["家教服务", "/tutors"],
  ["学霸社群", "/community"],
  ["关于我们", "/about"],
];

export function Brand({ footer = false }) {
  return (
    <a href="#/home" className={`flex items-center space-x-2 ${footer ? "text-white mb-4" : ""}`}>
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-blue text-white font-bold">
        游
      </div>
      <span className={`text-xl font-bold ${footer ? "" : "text-brand-dark"}`}>游知领航</span>
    </a>
  );
}

export function Header({ route, authUser, onLogin, onRegister, onLogout }) {
  return (
    <header className="sticky top-0 z-40 w-full border-b bg-white/80 backdrop-blur-md shadow-sm">
      <div className="container mx-auto flex h-16 items-center justify-between px-4 lg:px-8">
        <Brand />
        <nav className="hidden md:flex items-center space-x-8" aria-label="主导航">
          {navItems.map(([label, path]) => (
            <a
              key={path}
              href={`#${path}`}
              className={`text-sm font-medium transition-colors hover:text-brand-blue ${
                route === path ? "text-brand-blue" : "text-slate-600"
              }`}
            >
              {label}
            </a>
          ))}
        </nav>
        <div className="flex items-center space-x-4">
          {authUser ? (
            <>
              <a href="#/profile" className="header-user" aria-label={`个人中心：${authUser.displayName}`}>
                <span className="header-user-avatar">{authUser.displayName.slice(0, 1)}</span>
                <span className="hidden sm:inline">{authUser.displayName}</span>
              </a>
              <button className="header-logout" onClick={onLogout} aria-label="退出登录">
                <LogOut size={16} />
                <span className="hidden sm:inline">退出</span>
              </button>
            </>
          ) : (
            <>
              <button
                onClick={onLogin}
                className="hidden sm:flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium h-9 px-3 hover:bg-slate-100"
              >
                登录
              </button>
              <button
                onClick={onRegister}
                className="inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium h-9 px-4 bg-brand-orange text-white shadow hover:bg-brand-orange/80"
              >
                快速注册
              </button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

export function Footer() {
  return (
    <footer className="bg-brand-dark text-slate-300 py-12">
      <div className="container mx-auto px-4 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          <div className="col-span-1 md:col-span-1">
            <Brand footer />
            <p className="text-sm leading-relaxed">
              连接优质大学生家教与有需求的学生/家长。游戏启智，领航成长。
            </p>
          </div>
          <FooterLinks
            title="快速链接"
            links={[
              ["首页", "/home"],
              ["内容广场", "/content"],
              ["家教服务", "/tutors"],
              ["学霸社群", "/community"],
            ]}
          />
          <FooterLinks
            title="关于我们"
            links={[
              ["项目背景", "/about"],
              ["运营成果", "/about"],
              ["核心团队", "/about"],
              ["发展规划", "/about"],
            ]}
          />
          <div>
            <h4 className="text-white font-semibold mb-4">联系我们</h4>
            <ul className="space-y-2 text-sm">
              <li>邮箱：contact@youzhilinghang.com</li>
              <li>电话：400-123-4567</li>
              <li>地址：北京市海淀区某创业孵化基地</li>
            </ul>
          </div>
        </div>
        <div className="mt-12 pt-8 border-t border-slate-800 text-center text-xs text-slate-500">
          © 2026 游知领航 (YouZhiLingHang). All rights reserved.
        </div>
      </div>
    </footer>
  );
}

function FooterLinks({ title, links }) {
  return (
    <div>
      <h4 className="text-white font-semibold mb-4">{title}</h4>
      <ul className="space-y-2 text-sm">
        {links.map(([label, path]) => (
          <li key={`${title}-${label}`}>
            <a href={`#${path}`} className="hover:text-brand-blue transition-colors">
              {label}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function Layout({ route, children }) {
  const scrollRef = useRef(null);
  const [authUser, setAuthUser] = useState(null);
  const [authMode, setAuthMode] = useState("login");
  const [authOpen, setAuthOpen] = useState(false);
  const [toast, setToast] = useState("");

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0 });
  }, [route]);

  useEffect(() => {
    if (!api.hasSession()) return;
    api.me().then(setAuthUser).catch(() => api.clearSession());
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const openAuth = (mode) => {
    setAuthMode(mode);
    setAuthOpen(true);
  };

  const logout = async () => {
    try {
      await api.logout();
    } catch {
      // Clearing the local session still safely signs out this browser.
    }
    api.clearSession();
    setAuthUser(null);
    setToast("已安全退出");
    window.location.hash = "#/home";
  };

  return (
    <div className="flex flex-col h-screen bg-brand-light font-sans overflow-hidden">
      <Header
        route={route}
        authUser={authUser}
        onLogin={() => openAuth("login")}
        onRegister={() => openAuth("register")}
        onLogout={logout}
      />
      <div ref={scrollRef} className="flex-1 overflow-y-auto overflow-x-hidden" data-page-scroll>
        <main className="min-h-full flex flex-col">
          <div className="flex-1">{children}</div>
          <Footer />
        </main>
      </div>
      <AuthModal
        open={authOpen}
        initialMode={authMode}
        onClose={() => setAuthOpen(false)}
        onAuthenticated={(user, message) => {
          setAuthUser(user);
          setToast(message);
          window.location.hash = "#/profile";
        }}
      />
      <Toast message={toast} />
    </div>
  );
}
