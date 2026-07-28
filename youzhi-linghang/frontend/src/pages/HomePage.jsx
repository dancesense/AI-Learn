import { BookOpen, Play, Users } from "lucide-react";
import { Card } from "../components/Ui";
import { fallbackContents, fallbackTutors, stats } from "../data/fallback";

export function HomePage() {
  return (
    <>
      <section className="relative overflow-hidden bg-gradient-to-br from-brand-blue to-[#1a5f7a] py-20 lg:py-32 text-white">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="flex flex-col lg:flex-row items-center justify-between gap-12">
            <div className="max-w-2xl text-center lg:text-left">
              <h1 className="text-4xl md:text-6xl font-extrabold leading-tight mb-6">游戏启智，领航成长</h1>
              <p className="text-lg md:text-xl text-blue-100 mb-10 leading-relaxed">
                正向引导·优质家教 | 专注青少年价值观引导 & 大学生兼职孵化平台
              </p>
              <div className="flex flex-wrap justify-center lg:justify-start gap-4">
                <a href="#/tutors">
                  <button className="inline-flex h-12 items-center justify-center rounded-full bg-brand-orange px-10 text-base font-medium text-white shadow hover:bg-brand-orange/80">
                    找家教
                  </button>
                </a>
                <a href="#/profile">
                  <button className="inline-flex h-12 items-center justify-center rounded-full border border-white px-10 text-base font-medium text-white hover:bg-white/10">
                    成为师资
                  </button>
                </a>
              </div>
            </div>
            <div className="relative w-full max-w-lg">
              <div className="aspect-square rounded-3xl bg-white/10 backdrop-blur-sm border border-white/20 p-8 flex items-center justify-center">
                <img src="/assets/education.jpg" alt="Education" className="rounded-2xl shadow-2xl" />
              </div>
              <div className="absolute -top-6 -right-6 h-24 w-24 bg-brand-orange rounded-full blur-3xl opacity-50" />
              <div className="absolute -bottom-6 -left-6 h-32 w-32 bg-blue-400 rounded-full blur-3xl opacity-40" />
            </div>
          </div>
        </div>
      </section>

      <section className="py-20 bg-white">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            <FeatureCard
              icon={<Play className="h-8 w-8" />}
              tone="blue"
              title="游戏化学习"
              text="通过游戏机制激发学习兴趣，让知识吸收更高效、更有趣。"
            />
            <FeatureCard
              icon={<Users className="h-8 w-8" />}
              tone="orange"
              title="名校家教"
              text="双一流大学学子一对一辅导，不仅是老师，更是榜样与伙伴。"
            />
            <FeatureCard
              icon={<BookOpen className="h-8 w-8" />}
              tone="green"
              title="学霸社群"
              text="打卡陪伴，高效学习，与志同道合的伙伴一起共同进步。"
            />
          </div>
        </div>
      </section>

      <section className="py-16 bg-brand-light">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-8">
            {stats.map((stat) => (
              <div key={stat.label} className="text-center">
                <div className="text-4xl md:text-5xl font-extrabold text-brand-orange mb-2">{stat.value}</div>
                <div className="text-slate-500 font-medium">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="py-20 bg-white">
        <div className="container mx-auto px-4 lg:px-8">
          <SectionHeader title="热门视频推荐" color="blue" href="/content" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {fallbackContents.map((item) => (
              <Card key={item.id} className="group overflow-hidden border-slate-100 hover:shadow-xl cursor-pointer">
                <div className="aspect-video relative overflow-hidden">
                  <img
                    src={item.cover}
                    alt={item.title}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  />
                  <div className="absolute inset-0 bg-black/20 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                    <div className="h-12 w-12 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center">
                      <Play className="h-6 w-6 text-white fill-white" />
                    </div>
                  </div>
                </div>
                <div className="p-4">
                  <h3 className="font-bold text-slate-800 line-clamp-2 mb-2 group-hover:text-brand-blue transition-colors">
                    {item.title}
                  </h3>
                  <div className="flex items-center justify-between text-xs text-slate-500">
                    <span>{item.views} 播放</span>
                    <span>{item.category}</span>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <section className="py-20 bg-brand-light">
        <div className="container mx-auto px-4 lg:px-8">
          <SectionHeader title="优质师资推荐" color="orange" href="/tutors" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {fallbackTutors.slice(0, 4).map((tutor) => (
              <Card key={tutor.id} className="border-none text-center hover:shadow-xl">
                <div className="p-6 pt-8">
                  <img
                    src={tutor.avatar}
                    alt={tutor.name}
                    className="h-20 w-20 rounded-full object-cover mx-auto mb-4 border-4 border-white shadow"
                  />
                  <h3 className="text-xl font-bold text-slate-800 mb-1">{tutor.name}</h3>
                  <p className="text-slate-500 text-sm mb-4">{tutor.school}</p>
                  <div className="flex flex-wrap justify-center gap-2 mb-4">
                    {tutor.subjects.map((subject) => (
                      <span key={subject} className="px-2 py-1 bg-blue-50 text-brand-blue text-xs rounded">
                        {subject}
                      </span>
                    ))}
                  </div>
                  <div>
                    <span className="text-2xl font-bold text-brand-orange">¥{tutor.price}</span>
                    <span className="text-xs text-slate-400"> /小时</span>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <section className="py-20 bg-brand-blue text-white">
        <div className="container mx-auto px-4 text-center">
          <h2 className="text-3xl md:text-4xl font-bold mb-6">准备好开启成长之旅了吗？</h2>
          <p className="text-blue-100 text-lg mb-10">加入游知领航，让学习变得更有趣，让成长更有方向。</p>
          <a href="#/tutors">
            <button className="inline-flex h-12 items-center justify-center rounded-full bg-brand-orange px-12 text-base font-medium text-white shadow hover:bg-brand-orange/80">
              立即找家教
            </button>
          </a>
        </div>
      </section>
    </>
  );
}

function FeatureCard({ icon, tone, title, text }) {
  const tones = {
    blue: "bg-blue-50 text-brand-blue",
    orange: "bg-orange-50 text-brand-orange",
    green: "bg-green-50 text-green-600",
  };
  return (
    <Card className="border-none shadow-lg hover:translate-y-[-8px]">
      <div className="p-6 pt-10 text-center">
        <div className={`mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl ${tones[tone]}`}>
          {icon}
        </div>
        <h3 className="text-xl font-bold mb-3">{title}</h3>
        <p className="text-slate-500">{text}</p>
      </div>
    </Card>
  );
}

function SectionHeader({ title, color, href }) {
  return (
    <div className="flex items-center justify-between mb-10">
      <div className="flex items-center space-x-3">
        <div className={`w-1.5 h-8 rounded-full ${color === "orange" ? "bg-brand-orange" : "bg-brand-blue"}`} />
        <h2 className="text-2xl md:text-3xl font-bold">{title}</h2>
      </div>
      <a
        href={`#${href}`}
        className={`${color === "orange" ? "text-brand-orange" : "text-brand-blue"} hover:underline flex items-center`}
      >
        查看更多 →
      </a>
    </div>
  );
}
