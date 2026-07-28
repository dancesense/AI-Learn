import { LockKeyhole, Mail, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { Modal } from "./Ui";

const initialForm = {
  displayName: "",
  email: "",
  password: "",
  role: "青少年",
};

export function AuthModal({ open, initialMode = "login", onClose, onAuthenticated }) {
  const [mode, setMode] = useState(initialMode);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setMode(initialMode);
    setError("");
    setForm(initialForm);
  }, [open, initialMode]);

  const update = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const response =
        mode === "login"
          ? await api.login({ email: form.email, password: form.password })
          : await api.register(form);
      const user = api.saveSession(response);
      onAuthenticated(user, mode === "login" ? "登录成功" : "注册成功，欢迎加入");
      onClose();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={submitting ? undefined : onClose}
      title={mode === "login" ? "欢迎回来" : "创建游知账号"}
      description={mode === "login" ? "登录后继续您的成长之旅" : "只需一分钟，即可开始使用全部服务"}
    >
      <div className="auth-switch" aria-label="登录注册切换">
        <button
          type="button"
          className={mode === "login" ? "auth-switch-active" : ""}
          onClick={() => {
            setMode("login");
            setError("");
          }}
        >
          登录
        </button>
        <button
          type="button"
          className={mode === "register" ? "auth-switch-active" : ""}
          onClick={() => {
            setMode("register");
            setError("");
          }}
        >
          注册
        </button>
      </div>

      <form className="auth-form" onSubmit={submit}>
        {mode === "register" ? (
          <label className="form-field">
            <span>昵称</span>
            <div className="auth-input">
              <UserRound size={18} />
              <input
                value={form.displayName}
                onChange={update("displayName")}
                placeholder="请输入昵称"
                autoComplete="name"
                minLength={2}
                maxLength={30}
                required
              />
            </div>
          </label>
        ) : null}

        <label className="form-field">
          <span>邮箱</span>
          <div className="auth-input">
            <Mail size={18} />
            <input
              type="email"
              value={form.email}
              onChange={update("email")}
              placeholder="name@example.com"
              autoComplete="email"
              required
            />
          </div>
        </label>

        <label className="form-field">
          <span>密码</span>
          <div className="auth-input">
            <LockKeyhole size={18} />
            <input
              type="password"
              value={form.password}
              onChange={update("password")}
              placeholder="至少 6 位字符"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={6}
              maxLength={72}
              required
            />
          </div>
        </label>

        {mode === "register" ? (
          <label className="form-field">
            <span>身份</span>
            <select value={form.role} onChange={update("role")}>
              <option value="青少年">青少年</option>
              <option value="家长">家长</option>
              <option value="大学生">大学生</option>
            </select>
          </label>
        ) : null}

        {error ? <p className="auth-error" role="alert">{error}</p> : null}

        <button className="auth-submit" type="submit" disabled={submitting}>
          {submitting ? "提交中..." : mode === "login" ? "登录" : "立即注册"}
        </button>
      </form>

    </Modal>
  );
}
