import { Heart, MessageCircle, Search, TrendingUp } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Avatar, Card, Toast } from "../components/Ui";
import { fallbackContents } from "../data/fallback";
import { api } from "../lib/api";

const categories = ["全部", "游戏科普", "学习方法", "学科知识", "成长故事"];
const creators = [
  { name: "学霸小王", fans: "1.2w", avatar: "A1" },
  { name: "名校学子", fans: "8.5k", avatar: "A2" },
  { name: "物理大咖", fans: "5.2k", avatar: "A3" },
  { name: "励志学姐", fans: "15w", avatar: "A4" },
];

export function ContentPage() {
  const [category, setCategory] = useState("全部");
  const [query, setQuery] = useState("");
  const [items, setItems] = useState(fallbackContents);
  const [followed, setFollowed] = useState({});
  const [toast, setToast] = useState("");

  useEffect(() => {
    let active = true;
    api
      .getContents({ category, q: query })
      .then((data) => active && setItems(data))
      .catch(() => {
        const normalized = query.trim().toLowerCase();
        setItems(
          fallbackContents.filter(
            (item) =>
              (category === "全部" || item.category === category) &&
              (!normalized ||
                item.title.toLowerCase().includes(normalized) ||
                item.author.toLowerCase().includes(normalized)),
          ),
        );
      });
    return () => {
      active = false;
    };
  }, [category, query]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const topics = useMemo(
    () => ["#数学思维", "#英语打卡", "#高考加油", "#学习方法", "#游戏化教育", "#名校生活", "#编程入门"],
    [],
  );

  const toggleLike = async (item) => {
    setItems((current) =>
      current.map((entry) =>
        entry.id === item.id
          ? { ...entry, liked: !entry.liked, likes: entry.likes + (entry.liked ? -1 : 1) }
          : entry,
      ),
    );
    try {
      const result = await api.likeContent(item.id);
      setToast(result.message);
    } catch {
      setToast(item.liked ? "已取消点赞" : "点赞成功");
    }
  };

  const toggleFollow = async (name) => {
    const next = !followed[name];
    setFollowed((current) => ({ ...current, [name]: next }));
    try {
      const result = await api.followCreator(name);
      setToast(result.message);
    } catch {
      setToast(next ? "关注成功" : "已取消关注");
    }
  };

  return (
    <div className="container mx-auto px-4 lg:px-8 py-12">
      <div className="flex flex-col md:flex-row md:items-center justify-between mb-12 gap-6">
        <div>
          <h1 className="text-3xl font-bold text-slate-800 mb-2">内容广场</h1>
          <p className="text-slate-500">发现有趣的学习内容，与学霸交流心得</p>
        </div>
        <label className="relative w-full md:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="flex h-10 w-full rounded-full border border-slate-200 bg-white px-3 py-2 pl-10 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
            placeholder="搜索感兴趣的内容..."
          />
        </label>
      </div>

      <div className="flex flex-wrap gap-3 mb-10">
        {categories.map((item) => (
          <button
            key={item}
            onClick={() => setCategory(item)}
            className={`px-6 py-2 rounded-full text-sm font-medium transition-all ${
              category === item
                ? "bg-brand-blue text-white shadow-md"
                : "bg-white text-slate-600 hover:bg-slate-50"
            }`}
          >
            {item}
          </button>
        ))}
      </div>

      <div className="flex flex-col lg:flex-row gap-8">
        <div className="flex-1">
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
            {items.map((item) => (
              <Card key={item.id} className="group overflow-hidden border-none shadow-sm hover:shadow-xl">
                <div className="aspect-[9/16] relative overflow-hidden bg-slate-200">
                  <img
                    src={item.cover}
                    alt={item.title}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-80" />
                  <div className="absolute bottom-4 left-4 right-4">
                    <h3 className="text-white font-bold text-lg leading-tight mb-3 line-clamp-2">{item.title}</h3>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2">
                        <Avatar
                          src={item.authorAvatar}
                          fallback={item.author[0]}
                          className="h-8 w-8 border border-white/20"
                        />
                        <span className="text-white text-sm font-medium">{item.author}</span>
                      </div>
                      <div className="flex items-center space-x-3 text-white/90">
                        <button
                          className="flex items-center text-xs"
                          aria-label={`${item.liked ? "取消点赞" : "点赞"} ${item.title}`}
                          onClick={() => toggleLike(item)}
                        >
                          <Heart className={`h-4 w-4 mr-1 ${item.liked ? "fill-white" : ""}`} />
                          {item.likes}
                        </button>
                        <span className="flex items-center text-xs">
                          <MessageCircle className="h-4 w-4 mr-1" />
                          {item.comments}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </Card>
            ))}
            {[1, 2].map((item) => (
              <Card key={`loading-${item}`} className="group overflow-hidden border-none shadow-sm opacity-60">
                <div className="aspect-[9/16] relative overflow-hidden bg-slate-200 flex items-center justify-center">
                  <span className="text-slate-400 text-sm">更多内容正在加载...</span>
                </div>
              </Card>
            ))}
          </div>
        </div>

        <aside className="w-full lg:w-80 space-y-8">
          <Card className="border-none shadow-sm bg-blue-50/50">
            <div className="p-6">
              <div className="flex items-center space-x-2 mb-6">
                <TrendingUp className="h-5 w-5 text-brand-blue" />
                <h3 className="font-bold text-slate-800">热门话题</h3>
              </div>
              <div className="flex flex-wrap gap-2">
                {topics.map((topic) => (
                  <button
                    key={topic}
                    onClick={() => setQuery(topic.slice(1))}
                    className="inline-flex items-center rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-600 hover:border-brand-blue hover:text-brand-blue"
                  >
                    {topic}
                  </button>
                ))}
              </div>
            </div>
          </Card>

          <Card className="border-none shadow-sm">
            <div className="p-6">
              <h3 className="font-bold text-slate-800 mb-6">活跃创作者</h3>
              <div className="space-y-4">
                {creators.map((creator) => (
                  <div key={creator.name} className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <Avatar fallback={creator.avatar} />
                      <div>
                        <p className="text-sm font-bold text-slate-800">{creator.name}</p>
                        <p className="text-xs text-slate-500">{creator.fans} 粉丝</p>
                      </div>
                    </div>
                    <button
                      onClick={() => toggleFollow(creator.name)}
                      className="text-xs font-bold text-brand-blue hover:underline"
                    >
                      {followed[creator.name] ? "已关注" : "关注"}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        </aside>
      </div>
      <Toast message={toast} />
    </div>
  );
}
