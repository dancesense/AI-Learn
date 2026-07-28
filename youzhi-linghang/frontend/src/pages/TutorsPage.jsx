import { ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Card, Modal, Toast } from "../components/Ui";
import { fallbackTutors } from "../data/fallback";
import { api } from "../lib/api";

const subjects = ["全部", "数学", "物理", "化学", "英语", "语文"];
const grades = ["全部", "小学", "初中", "高中"];
const prices = ["全部", "<50", "50-100", "100-150", ">150"];

export function TutorsPage() {
  const [subject, setSubject] = useState("全部");
  const [grade, setGrade] = useState("全部");
  const [priceRange, setPriceRange] = useState("全部");
  const [query, setQuery] = useState("");
  const [tutors, setTutors] = useState(fallbackTutors);
  const [selectedTutor, setSelectedTutor] = useState(null);
  const [selectedSubject, setSelectedSubject] = useState("");
  const [scheduledAt, setScheduledAt] = useState(defaultTomorrow());
  const [toast, setToast] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .getTutors({ subject, grade, priceRange, q: query })
      .then((data) => active && setTutors(data))
      .catch(() => {
        const normalized = query.trim().toLowerCase();
        setTutors(
          fallbackTutors.filter(
            (tutor) =>
              (subject === "全部" || tutor.subjects.some((item) => item.includes(subject))) &&
              (grade === "全部" || tutor.grades.includes(grade)) &&
              matchesPrice(tutor.price, priceRange) &&
              (!normalized ||
                tutor.name.toLowerCase().includes(normalized) ||
                tutor.school.toLowerCase().includes(normalized)),
          ),
        );
      });
    return () => {
      active = false;
    };
  }, [subject, grade, priceRange, query]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2400);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const openReservation = (tutor) => {
    setSelectedTutor(tutor);
    setSelectedSubject(tutor.subjects[0]);
    setScheduledAt(defaultTomorrow());
  };

  const submitReservation = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      const reservation = await api.createReservation({
        tutorId: selectedTutor.id,
        subject: selectedSubject,
        scheduledAt,
      });
      setToast(`预约成功，订单号 ${reservation.orderNo}`);
      setSelectedTutor(null);
    } catch (error) {
      if (error instanceof TypeError) {
        setToast("预约已保存（演示模式）");
        setSelectedTutor(null);
      } else {
        setToast(error.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const empty = useMemo(() => tutors.length === 0, [tutors]);

  return (
    <div className="container mx-auto px-4 lg:px-8 py-12">
      <div className="mb-12">
        <h1 className="text-3xl font-bold text-slate-800 mb-2">家教服务大厅</h1>
        <p className="text-slate-500">挑选最适合您的名校导师</p>
      </div>

      <Card className="mb-10 border-none shadow-sm p-6">
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
          <div className="lg:col-span-3 space-y-6">
            <FilterRow label="科目筛选:" options={subjects} value={subject} onChange={setSubject} />
            <FilterRow label="年级筛选:" options={grades} value={grade} onChange={setGrade} />
            <FilterRow label="价格区间:" options={prices} value={priceRange} onChange={setPriceRange} />
          </div>
          <div className="flex items-end">
            <label className="relative w-full">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="flex h-11 w-full rounded-md border border-slate-200 bg-white px-3 py-2 pl-10 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
                placeholder="搜索姓名或院校..."
              />
            </label>
          </div>
        </div>
      </Card>

      {empty ? (
        <Card className="border-none p-8 text-center text-slate-500">没有找到符合条件的导师，请调整筛选条件。</Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 mb-12">
          {tutors.map((tutor) => (
            <Card key={tutor.id} className="group hover:shadow-xl border-slate-100 overflow-hidden">
              <div className="p-6 pt-8 text-center">
                <div className="relative inline-block mb-6">
                  <img
                    src={tutor.avatar}
                    alt={tutor.name}
                    className="h-28 w-28 rounded-full object-cover border-4 border-white shadow-lg"
                  />
                  <div
                    className="absolute bottom-1 right-1 h-6 w-6 rounded-full bg-green-500 border-2 border-white flex items-center justify-center"
                    title="在线"
                  >
                    <div className="h-2 w-2 rounded-full bg-white animate-pulse" />
                  </div>
                </div>
                <h3 className="text-xl font-bold text-slate-800 mb-1">{tutor.name}</h3>
                <p className="text-brand-blue font-medium mb-4">{tutor.school}</p>
                <div className="flex flex-wrap justify-center gap-2 mb-4">
                  {tutor.tags.map((tag) => (
                    <span key={tag} className="inline-flex rounded-md bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-brand-blue">
                      {tag}
                    </span>
                  ))}
                </div>
                <div className="text-sm text-slate-500 mb-6 min-h-10">
                  <span className="font-medium text-slate-600">擅长: </span>
                  {tutor.subjects.join("、")}
                </div>
                <div className="border-t border-slate-100 pt-6 flex items-end justify-between text-left">
                  <div>
                    <p className="text-xs text-slate-400 mb-1">辅导价格</p>
                    <p className="text-2xl font-bold text-brand-orange">
                      ¥{Number(tutor.price).toFixed(0)}
                      <span className="text-xs font-normal">/小时</span>
                    </p>
                  </div>
                  <button
                    onClick={() => openReservation(tutor)}
                    className="inline-flex h-10 items-center justify-center rounded-full bg-brand-blue px-6 text-sm font-medium text-white shadow hover:bg-brand-blue/80"
                  >
                    立即预约
                  </button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <div className="flex justify-center items-center space-x-2">
        <button className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 bg-white">
          <ChevronLeft className="h-4 w-4" />
        </button>
        <button className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-brand-blue text-white">1</button>
        <button className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 bg-white">2</button>
        <button className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 bg-white">3</button>
        <span className="px-2 text-slate-400">...</span>
        <button className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 bg-white">12</button>
        <button className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 bg-white">
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>

      <Modal
        open={Boolean(selectedTutor)}
        onClose={() => setSelectedTutor(null)}
        title={`预约 ${selectedTutor?.name || ""}`}
        description="选择辅导科目和期望上课时间，提交后导师会与您确认。"
      >
        {selectedTutor ? (
          <form onSubmit={submitReservation} className="space-y-4">
            <label className="form-field">
              <span>辅导科目</span>
              <select value={selectedSubject} onChange={(event) => setSelectedSubject(event.target.value)}>
                {selectedTutor.subjects.map((item) => (
                  <option key={item}>{item}</option>
                ))}
              </select>
            </label>
            <label className="form-field">
              <span>上课时间</span>
              <input
                type="datetime-local"
                min={defaultTomorrow()}
                value={scheduledAt}
                onChange={(event) => setScheduledAt(event.target.value)}
                required
              />
            </label>
            <button
              disabled={submitting}
              className="inline-flex h-11 w-full items-center justify-center rounded-full bg-brand-blue px-6 text-sm font-medium text-white disabled:opacity-60"
            >
              {submitting ? "提交中..." : "确认预约"}
            </button>
          </form>
        ) : null}
      </Modal>
      <Toast message={toast} />
    </div>
  );
}

function FilterRow({ label, options, value, onChange }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center gap-4">
      <span className="text-sm font-bold text-slate-600 w-20 shrink-0">{label}</span>
      <div className="flex flex-wrap gap-2">
        {options.map((item) => (
          <button
            key={item}
            onClick={() => onChange(item)}
            className={`px-4 py-1.5 rounded-lg text-sm transition-all ${
              value === item ? "bg-brand-blue text-white" : "bg-slate-50 text-slate-600 hover:bg-slate-100"
            }`}
          >
            {item}
          </button>
        ))}
      </div>
    </div>
  );
}

function matchesPrice(price, range) {
  if (range === "<50") return price < 50;
  if (range === "50-100") return price >= 50 && price <= 100;
  if (range === "100-150") return price > 100 && price <= 150;
  if (range === ">150") return price > 150;
  return true;
}

function defaultTomorrow() {
  const value = new Date();
  value.setDate(value.getDate() + 1);
  value.setHours(19, 0, 0, 0);
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
