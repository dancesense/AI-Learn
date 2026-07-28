import { Bell, CalendarDays, MessageSquare, Users } from "lucide-react";
import { useEffect, useState } from "react";
import { Avatar, Card, Toast } from "../components/Ui";
import { fallbackCommunities } from "../data/fallback";
import { api } from "../lib/api";

const activities = [
  { user: "张三", action: "加入了", target: "数学思维挑战营", time: "5分钟前" },
  { user: "李四", action: "在", target: "英语口语打卡群", time: "12分钟前", comment: "打卡第21天，坚持就是胜利！" },
  { user: "王五", action: "发布了动态", target: "学霸社群", time: "30分钟前" },
  { user: "赵六", action: "分享了资料", target: "高考志愿填报交流", time: "1小时前" },
];

export function CommunityPage() {
  const [communities, setCommunities] = useState(fallbackCommunities);
  const [toast, setToast] = useState("");

  useEffect(() => {
    api.getCommunities().then(setCommunities).catch(() => setCommunities(fallbackCommunities));
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const join = async (community) => {
    const next = !community.joined;
    setCommunities((items) =>
      items.map((item) =>
        item.id === community.id
          ? { ...item, joined: next, members: item.members + (next ? 1 : -1) }
          : item,
      ),
    );
    try {
      const result = await api.joinCommunity(community.id);
      setToast(result.message);
    } catch {
      setToast(next ? "加入社群成功" : "已退出社群");
    }
  };

  return (
    <div className="container mx-auto px-4 lg:px-8 py-12">
      <div className="mb-12">
        <h1 className="text-3xl font-bold text-slate-800 mb-2">学霸社群</h1>
        <p className="text-slate-500">与志同道合的伙伴一起交流成长</p>
      </div>
      <div className="flex flex-col lg:flex-row gap-8">
        <div className="flex-1 space-y-8">
          <Card className="bg-gradient-to-r from-brand-blue to-blue-500 text-white border-none shadow-lg">
            <div className="p-8">
              <div className="flex flex-col md:flex-row items-center gap-8">
                <div className="flex-1">
                  <h2 className="text-2xl font-bold mb-4">游知领航社群理念</h2>
                  <p className="text-blue-50 leading-relaxed mb-6">
                    我们相信陪伴的力量。在这里，你可以找到志同道合的学习伙伴，
                    由名校学霸亲自带领，通过打卡、交流、分享，让学习不再孤单。
                  </p>
                  <div className="flex flex-wrap gap-4">
                    <div className="flex items-center space-x-2 bg-white/10 px-4 py-2 rounded-lg">
                      <Users className="h-4 w-4" />
                      <span className="text-sm">5000+ 成员</span>
                    </div>
                    <div className="flex items-center space-x-2 bg-white/10 px-4 py-2 rounded-lg">
                      <MessageSquare className="h-4 w-4" />
                      <span className="text-sm">20w+ 互动</span>
                    </div>
                  </div>
                </div>
                <div className="hidden md:block w-48 h-48 bg-white/20 rounded-2xl backdrop-blur-sm p-4 border border-white/20">
                  <img src="/assets/community-hero.jpg" alt="Community" className="rounded-xl" />
                </div>
              </div>
            </div>
          </Card>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {communities.map((community) => (
              <Card key={community.id} className="hover:shadow-md border-slate-100">
                <div className="h-32 bg-slate-200 relative overflow-hidden">
                  <img src={community.cover} alt={community.name} className="w-full h-full object-cover" />
                  <div className="absolute top-4 right-4 bg-white/80 backdrop-blur-sm px-2 py-1 rounded text-xs font-bold text-brand-blue">
                    {community.members} 成员
                  </div>
                </div>
                <div className="p-6">
                  <h3 className="text-lg font-bold text-slate-800 mb-2">{community.name}</h3>
                  <p className="text-sm text-slate-500 line-clamp-2 mb-6">{community.description}</p>
                  <button
                    onClick={() => join(community)}
                    className={`inline-flex h-10 w-full items-center justify-center rounded-full px-6 text-sm font-medium text-white ${
                      community.joined ? "bg-slate-500" : "bg-brand-blue hover:bg-brand-blue/80"
                    }`}
                  >
                    {community.joined ? "已加入" : "立即加入"}
                  </button>
                </div>
              </Card>
            ))}
          </div>
        </div>

        <aside className="w-full lg:w-96 space-y-8">
          <Card className="border-none shadow-sm">
            <div className="flex flex-col space-y-1.5 p-6">
              <h3 className="flex items-center space-x-2 font-semibold leading-none tracking-tight text-lg">
                <Bell className="h-5 w-5 text-brand-orange" />
                <span>社群规则</span>
              </h3>
            </div>
            <div className="p-6 pt-0 space-y-4">
              {[
                "尊重他人，保持友善的交流环境。",
                "禁止发布任何形式的广告及不良信息。",
                "鼓励分享学习心得和干货资料。",
              ].map((rule, index) => (
                <div key={rule} className="flex items-start space-x-3">
                  <div className="h-5 w-5 rounded-full bg-orange-100 text-brand-orange flex items-center justify-center text-xs shrink-0 mt-0.5">
                    {index + 1}
                  </div>
                  <p className="text-sm text-slate-600">{rule}</p>
                </div>
              ))}
            </div>
          </Card>

          <Card className="border-none shadow-sm">
            <div className="flex flex-col space-y-1.5 p-6">
              <h3 className="flex items-center space-x-2 font-semibold leading-none tracking-tight text-lg">
                <CalendarDays className="h-5 w-5 text-brand-blue" />
                <span>活跃动态</span>
              </h3>
            </div>
            <div className="p-6 pt-0">
              <div className="space-y-6">
                {activities.map((activity) => (
                  <div key={`${activity.user}-${activity.time}`} className="flex space-x-4">
                    <Avatar fallback={activity.user[0]} className="h-8 w-8" />
                    <div className="space-y-1">
                      <p className="text-sm text-slate-800">
                        <span className="font-bold">{activity.user}</span> {activity.action}{" "}
                        <span className="text-brand-blue font-medium">{activity.target}</span>
                      </p>
                      {activity.comment ? (
                        <div className="bg-slate-50 p-2 rounded-lg text-xs text-slate-500 italic">
                          “{activity.comment}”
                        </div>
                      ) : null}
                      <p className="text-[10px] text-slate-400">{activity.time}</p>
                    </div>
                  </div>
                ))}
              </div>
              <button className="inline-flex h-10 w-full items-center justify-center rounded-md px-4 text-sm font-medium mt-6 text-slate-400 hover:text-brand-blue hover:bg-slate-100">
                查看更多动态
              </button>
            </div>
          </Card>
        </aside>
      </div>
      <Toast message={toast} />
    </div>
  );
}
