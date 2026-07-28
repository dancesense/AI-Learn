import { Award, Rocket, Target, Trophy, Users } from "lucide-react";
import { Card } from "../components/Ui";
import { stats } from "../data/fallback";

const team = [
  { name: "王晓明", role: "创始人", description: "清华大学教育学硕士，5年在线教育创业经验。", avatar: "W" },
  { name: "李华", role: "教研总监", description: "北京大学心理学博士，专注青少年价值观引导。", avatar: "L" },
  { name: "张强", role: "技术负责人", description: "前一线互联网公司架构师，热爱教育事业。", avatar: "Z" },
];

const timeline = [
  { year: "2024.03", title: "项目立项", description: "游知领航团队正式组建，确定“游戏启智”核心理念。" },
  { year: "2024.10", title: "荣获大奖", description: "获得校级创新创业大赛二等奖。" },
  { year: "2025.01", title: "正式上线", description: "Web端与小程序同步上线，开启师资招募。" },
  { year: "2025.05", title: "孵化入驻", description: "正式入驻海淀区某大学生创业孵化基地。" },
];

export function AboutPage() {
  return (
    <>
      <section className="bg-slate-900 py-24 text-white">
        <div className="container mx-auto px-4 lg:px-8 text-center">
          <h1 className="text-4xl md:text-5xl font-bold mb-6">关于游知领航</h1>
          <p className="text-slate-400 max-w-3xl mx-auto text-lg leading-relaxed">
            游知领航致力于通过游戏化机制与名校师资陪伴，
            为青少年提供知识辅导以外的价值观引导与综合成长支持。
          </p>
        </div>
      </section>

      <section className="py-20 bg-white">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="flex flex-col lg:flex-row items-center gap-16">
            <div className="flex-1">
              <div className="inline-flex items-center space-x-2 bg-blue-50 text-brand-blue px-4 py-1 rounded-full text-sm font-bold mb-6">
                <Target className="h-4 w-4" />
                <span>项目背景</span>
              </div>
              <h2 className="text-3xl font-bold text-slate-800 mb-6">连接梦想，领航未来</h2>
              <div className="space-y-4 text-slate-600 leading-relaxed">
                <p>
                  在当今教育环境下，家长们不仅关注孩子的学科成绩，更关心孩子的心理健康与价值观形成。
                  而优秀的大学生群体，拥有充沛的精力和前沿的知识，却缺乏高质量的社会实践机会。
                </p>
                <p>
                  游知领航应运而生。我们不仅是一个家教平台，更是一个“成长伙伴”连接器。
                  我们筛选双一流名校学子，经过专业的教育心理学培训，
                  让他们在辅导学科的同时，通过游戏化的方式引导孩子建立正向的价值观。
                </p>
              </div>
            </div>
            <div className="flex-1 grid grid-cols-2 gap-4">
              <div className="space-y-4 pt-8">
                <ValueCard icon={<Trophy />} title="创新教育" tone="blue" />
                <ValueCard icon={<Rocket />} title="快速成长" tone="orange" />
              </div>
              <div className="space-y-4">
                <ValueCard icon={<Users />} title="名校社区" tone="green" />
                <ValueCard icon={<Award />} title="品质保证" tone="purple" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="py-20 bg-brand-light">
        <div className="container mx-auto px-4 lg:px-8">
          <h2 className="text-3xl font-bold text-center mb-16">运营成果</h2>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-8">
            {stats.map((stat) => (
              <div key={stat.label} className="bg-white rounded-2xl p-8 text-center shadow-sm">
                <div className="text-4xl font-extrabold text-brand-blue mb-2">{stat.value}</div>
                <div className="text-slate-500">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="py-20 bg-white">
        <div className="container mx-auto px-4 lg:px-8">
          <h2 className="text-3xl font-bold text-center mb-16">核心团队</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {team.map((member) => (
              <Card key={member.name} className="text-center hover:shadow-lg border-slate-100">
                <div className="p-8 pt-10">
                  <div className="h-24 w-24 mx-auto mb-6 rounded-full bg-slate-100 text-2xl font-bold text-brand-blue flex items-center justify-center">
                    {member.avatar}
                  </div>
                  <h3 className="text-xl font-bold text-slate-800 mb-1">{member.name}</h3>
                  <p className="text-brand-blue text-sm font-medium mb-4">{member.role}</p>
                  <p className="text-slate-500 text-sm leading-relaxed">{member.description}</p>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <section className="py-20 bg-slate-50">
        <div className="container mx-auto px-4 lg:px-8">
          <h2 className="text-3xl font-bold text-center mb-16">发展规划</h2>
          <div className="max-w-4xl mx-auto">
            <div className="timeline">
              {timeline.map((item, index) => (
                <div key={item.year} className={`timeline-item ${index % 2 === 0 ? "timeline-left" : "timeline-right"}`}>
                  <div className="timeline-dot">
                    <div />
                  </div>
                  <Card className="p-6 border-slate-100">
                    <span className="inline-block px-3 py-1 bg-blue-50 text-brand-blue text-xs font-bold rounded-full mb-3">
                      {item.year}
                    </span>
                    <h3 className="text-lg font-bold text-slate-800 mb-2">{item.title}</h3>
                    <p className="text-sm text-slate-500 leading-relaxed">{item.description}</p>
                  </Card>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>
    </>
  );
}

function ValueCard({ icon, title, tone }) {
  const tones = {
    blue: "bg-blue-50 text-brand-blue",
    orange: "bg-orange-50 text-brand-orange",
    green: "bg-green-50 text-green-600",
    purple: "bg-purple-50 text-purple-600",
  };
  return (
    <Card className={`${tones[tone]} border-none p-6 text-center`}>
      <div className="h-8 w-8 mx-auto mb-4">{icon}</div>
      <p className="font-bold text-slate-800">{title}</p>
    </Card>
  );
}
