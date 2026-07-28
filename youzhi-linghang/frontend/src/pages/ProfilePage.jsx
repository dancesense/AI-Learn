import {
  ChevronRight,
  CircleUser,
  FileText,
  GraduationCap,
  MessageSquare,
  Settings,
  ShieldCheck,
  Star,
  Users,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Avatar, Card, Modal, Toast } from "../components/Ui";
import { fallbackProfile } from "../data/fallback";
import { api } from "../lib/api";

const roleOptions = [
  { id: "青少年", icon: CircleUser, description: "获取个性化学习建议" },
  { id: "家长", icon: Users, description: "为孩子寻找优质导师" },
  { id: "大学生", icon: GraduationCap, description: "申请成为平台认证家教" },
];

export function ProfilePage() {
  const [profile, setProfile] = useState(fallbackProfile);
  const [roleModal, setRoleModal] = useState(false);
  const [draftRole, setDraftRole] = useState(fallbackProfile.role);
  const [reservations, setReservations] = useState([]);
  const [toast, setToast] = useState("");

  useEffect(() => {
    api.getProfile().then((data) => {
      setProfile(data);
      setDraftRole(data.role);
    }).catch(() => {});
    api.getReservations().then(setReservations).catch(() => {});
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const saveRole = async () => {
    try {
      const updated = await api.updateRole(draftRole);
      setProfile(updated);
    } catch {
      setProfile((current) => ({ ...current, role: draftRole }));
    }
    setRoleModal(false);
    setToast("身份已保存");
  };

  const menuItems = [
    { icon: Star, color: "text-yellow-500", label: "我的收藏", count: profile.collections },
    { icon: FileText, color: "text-blue-500", label: "我的订单", count: profile.orders },
    { icon: Users, color: "text-green-500", label: "我的社群", count: profile.communities },
    { icon: MessageSquare, color: "text-orange-500", label: "消息中心", count: profile.messages, highlight: true },
    { icon: Settings, color: "text-slate-500", label: "帮助与设置" },
  ];

  const currentReservation = reservations[0];

  return (
    <div className="bg-brand-light min-h-screen pb-20">
      <div className="bg-gradient-to-r from-brand-blue to-blue-600 pt-12 pb-24 text-white">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="flex flex-col md:flex-row items-center md:items-start gap-8">
            <Avatar src={profile.avatar} fallback="用户" className="h-32 w-32 border-4 border-white/20" />
            <div className="text-center md:text-left pt-4">
              <h1 className="text-3xl font-bold mb-2">{profile.displayName}</h1>
              <div className="flex flex-wrap justify-center md:justify-start gap-3 items-center">
                <button
                  onClick={() => setRoleModal(true)}
                  className="inline-flex items-center rounded-md bg-white/20 px-2.5 py-0.5 text-xs font-semibold text-white hover:bg-white/30"
                >
                  {profile.role}
                </button>
                <div className="flex items-center text-blue-100 text-sm">
                  <ShieldCheck className="h-4 w-4 mr-1" />
                  <span>{profile.verified ? "实名已认证" : "未认证"}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 lg:px-8 -mt-12">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <Card key={item.label} className="hover:shadow-md cursor-pointer border-none shadow-sm group">
                <div className="p-6 flex items-center justify-between">
                  <div className="flex items-center space-x-4">
                    <div className="p-3 rounded-xl bg-slate-50 group-hover:bg-slate-100">
                      <Icon className={`h-5 w-5 ${item.color}`} />
                    </div>
                    <span className="font-bold text-slate-700">{item.label}</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    {item.count ? (
                      <span
                        className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                          item.highlight ? "bg-brand-orange text-white" : "bg-slate-100 text-slate-500"
                        }`}
                      >
                        {item.count}
                      </span>
                    ) : null}
                    <ChevronRight className="h-4 w-4 text-slate-300 group-hover:text-brand-blue" />
                  </div>
                </div>
              </Card>
            );
          })}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mt-12">
          <Card className="border-none shadow-sm">
            <div className="flex flex-col space-y-1.5 p-6">
              <h3 className="font-semibold leading-none tracking-tight text-lg">最近浏览的内容</h3>
            </div>
            <div className="p-6 pt-0">
              <div className="space-y-4">
                {[1, 2, 3].map((item) => (
                  <div
                    key={item}
                    className="flex items-center space-x-4 p-2 hover:bg-slate-50 rounded-lg cursor-pointer"
                  >
                    <div className="h-16 w-24 bg-slate-200 rounded-lg overflow-hidden shrink-0">
                      <img src="/assets/history.jpg" alt="" className="w-full h-full object-cover" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-bold text-slate-800 truncate">如何通过游戏提升逻辑思维？</p>
                      <p className="text-xs text-slate-400 mt-1">2026-06-12</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </Card>

          <Card className="border-none shadow-sm">
            <div className="flex flex-col space-y-1.5 p-6">
              <h3 className="font-semibold leading-none tracking-tight text-lg">我的家教订单</h3>
            </div>
            <div className="p-6 pt-0">
              <div className="space-y-4">
                {currentReservation ? (
                  <div className="border border-slate-100 rounded-xl p-4">
                    <div className="flex justify-between mb-3">
                      <span className="text-xs font-bold text-slate-400">订单号: {currentReservation.orderNo}</span>
                      <span className="inline-flex rounded-md bg-green-50 px-2.5 py-0.5 text-xs font-semibold text-green-600">
                        {currentReservation.status}
                      </span>
                    </div>
                    <div className="flex items-center space-x-4">
                      <Avatar fallback={currentReservation.tutorName[0]} className="h-12 w-12" />
                      <div>
                        <p className="text-sm font-bold text-slate-800">
                          {currentReservation.tutorName} · {currentReservation.subject}
                        </p>
                        <p className="text-xs text-slate-500 mt-1">下次上课：明天 19:00</p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="profile-empty">暂无家教订单，去家教服务大厅看看吧</div>
                )}
                <button className="inline-flex h-10 w-full items-center justify-center rounded-md border border-brand-blue px-4 text-sm font-medium text-slate-500 hover:bg-slate-50">
                  查看全部订单
                </button>
              </div>
            </div>
          </Card>
        </div>
      </div>

      <Modal
        open={roleModal}
        onClose={() => setRoleModal(false)}
        title="选择您的身份"
        description="我们将根据您的身份为您推荐最合适的内容和服务"
      >
        <div className="space-y-4">
          {roleOptions.map((option) => {
            const Icon = option.icon;
            const selected = draftRole === option.id;
            return (
              <button
                key={option.id}
                onClick={() => setDraftRole(option.id)}
                className={`role-option ${selected ? "role-option-active" : ""}`}
              >
                <div className={`p-2 rounded-lg ${selected ? "text-brand-blue" : "text-slate-400"}`}>
                  <Icon className="h-8 w-8" />
                </div>
                <div className="text-left">
                  <p className="font-bold text-slate-800">{option.id}</p>
                  <p className="text-xs text-slate-500">{option.description}</p>
                </div>
                {selected ? <span className="role-radio"><span /></span> : null}
              </button>
            );
          })}
          <button onClick={saveRole} className="inline-flex h-11 w-full items-center justify-center rounded-full bg-brand-blue px-6 text-sm font-medium text-white">
            确认并保存
          </button>
        </div>
      </Modal>
      <Toast message={toast} />
    </div>
  );
}
