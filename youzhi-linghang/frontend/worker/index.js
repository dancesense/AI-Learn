const SESSION_DAYS = 7;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function now() {
  return new Date().toISOString();
}

function randomHex(bytes = 32) {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return [...value].map((item) => item.toString(16).padStart(2, "0")).join("");
}

function hexBytes(value) {
  return new Uint8Array(value.match(/.{1,2}/g).map((item) => Number.parseInt(item, 16)));
}

async function hashPassword(password, salt = randomHex(16)) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: hexBytes(salt), iterations: 100_000 },
    key,
    256,
  );
  const hash = [...new Uint8Array(bits)]
    .map((item) => item.toString(16).padStart(2, "0"))
    .join("");
  return `${salt}:${hash}`;
}

async function passwordMatches(password, stored) {
  const [salt, expected] = stored.split(":");
  if (!salt || !expected) return false;
  const actual = (await hashPassword(password, salt)).split(":")[1];
  if (actual.length !== expected.length) return false;
  let difference = 0;
  for (let index = 0; index < actual.length; index += 1) {
    difference |= actual.charCodeAt(index) ^ expected.charCodeAt(index);
  }
  return difference === 0;
}

function split(value) {
  return value ? value.split("|") : [];
}

function mapContent(row) {
  return {
    id: row.id,
    title: row.title,
    views: row.views,
    cover: row.cover,
    author: row.author,
    authorAvatar: row.author_avatar,
    likes: row.likes,
    comments: row.comments,
    category: row.category,
    liked: Boolean(row.liked),
  };
}

function mapTutor(row) {
  return {
    id: row.id,
    name: row.name,
    school: row.school,
    tags: split(row.tags),
    subjects: split(row.subjects),
    grades: split(row.grades),
    price: row.price,
    avatar: row.avatar,
    description: row.description,
    online: Boolean(row.online),
  };
}

function mapProfile(row) {
  return {
    id: row.id,
    displayName: row.display_name,
    role: row.role,
    verified: Boolean(row.verified),
    avatar: row.avatar,
    collections: row.collections_count,
    orders: row.orders_count,
    communities: row.communities_count,
    messages: row.messages_count,
  };
}

async function seedDatabase(db) {
  const row = await db.prepare("SELECT COUNT(*) AS count FROM users").first();
  if (Number(row?.count || 0) > 0) return;

  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);
  tomorrow.setHours(19, 0, 0, 0);
  await db.batch([
    db.prepare(`
      INSERT INTO users(
        id, display_name, role, verified, avatar,
        collections_count, orders_count, communities_count, messages_count
      ) VALUES (1, '游小知', '青少年', 1, '/assets/user.jpg', 12, 3, 5, 3)
    `),
    db.prepare(`
      INSERT INTO content_items(id, title, views, cover, author, author_avatar, likes, comments, category)
      VALUES (1, '如何通过游戏提升逻辑思维？', '1.2万', '/assets/video-1.jpg', '学霸小王', '/assets/avatar-1.jpg', 450, 32, '游戏科普')
    `),
    db.prepare(`
      INSERT INTO content_items(id, title, views, cover, author, author_avatar, likes, comments, category)
      VALUES (2, '清华学长的24小时学习法', '5.8万', '/assets/video-2.jpg', '名校学子', '/assets/avatar-2.jpg', 2300, 156, '学习方法')
    `),
    db.prepare(`
      INSERT INTO content_items(id, title, views, cover, author, author_avatar, likes, comments, category)
      VALUES (3, '初中物理必考知识点总结', '8500', '/assets/video-3.jpg', '物理大咖', '/assets/avatar-3.jpg', 310, 18, '学科知识')
    `),
    db.prepare(`
      INSERT INTO content_items(id, title, views, cover, author, author_avatar, likes, comments, category)
      VALUES (4, '从差生到学霸的逆袭之路', '3.2万', '/assets/video-4.jpg', '励志学姐', '/assets/avatar-4.jpg', 1200, 89, '成长故事')
    `),
    db.prepare(`
      INSERT INTO tutors(id, name, school, tags, subjects, grades, price, avatar, description, online)
      VALUES (1, '张一鸣', '清华大学', '双一流|数学系|3年经验', '初中数学|高中数学', '初中|高中', 150, '/assets/tutor-1.jpg', '擅长启发式教学，让孩子爱上数学。', 1)
    `),
    db.prepare(`
      INSERT INTO tutors(id, name, school, tags, subjects, grades, price, avatar, description, online)
      VALUES (2, '李诗雨', '北京大学', '英语专业|雅思8.0|口语达人', '小学英语|初中英语', '小学|初中', 120, '/assets/tutor-2.jpg', '纯正美式发音，互动式课堂。', 1)
    `),
    db.prepare(`
      INSERT INTO tutors(id, name, school, tags, subjects, grades, price, avatar, description, online)
      VALUES (3, '王博', '复旦大学', '物理竞赛|理综霸主', '高中物理|高中化学', '高中', 180, '/assets/tutor-3.jpg', '深入浅出讲解物理难点。', 1)
    `),
    db.prepare(`
      INSERT INTO tutors(id, name, school, tags, subjects, grades, price, avatar, description, online)
      VALUES (4, '赵悦', '浙江大学', '文综名师|语文满分', '小学语文|初中语文', '小学|初中', 100, '/assets/tutor-4.jpg', '培养阅读兴趣，提升写作能力。', 1)
    `),
    db.prepare(`
      INSERT INTO tutors(id, name, school, tags, subjects, grades, price, avatar, description, online)
      VALUES (5, '陈杰', '上海交通大学', '编程大牛|信息学奥赛', '少儿编程|初中数学', '小学|初中', 130, '/assets/tutor-5.jpg', '逻辑思维训练专家。', 1)
    `),
    db.prepare(`
      INSERT INTO communities(id, name, description, members, cover)
      VALUES (1, '数学思维挑战营', '每日一题，挑战思维极限，名校学霸带你刷题。', 1250, '/assets/community-1.jpg')
    `),
    db.prepare(`
      INSERT INTO communities(id, name, description, members, cover)
      VALUES (2, '英语口语打卡群', '纯正发音练习，每日语料推送，共同进步。', 890, '/assets/community-2.jpg')
    `),
    db.prepare(`
      INSERT INTO communities(id, name, description, members, cover)
      VALUES (3, '高考志愿填报交流', '学长学姐分享报考经验，解析专业前景。', 3400, '/assets/community-3.jpg')
    `),
    db.prepare(`
      INSERT INTO communities(id, name, description, members, cover)
      VALUES (4, '青少年编程俱乐部', 'Scratch、Python入门到进阶，作品展示台。', 670, '/assets/community-4.jpg')
    `),
    db.prepare(`
      INSERT INTO reservations(id, order_no, user_id, tutor_id, subject, scheduled_at, status, created_at)
      VALUES (1, 'YZ2026061501', 1, 1, '初中数学', ?, '进行中', ?)
    `).bind(tomorrow.toISOString(), now()),
  ]);
}

function bearer(request) {
  const value = request.headers.get("authorization");
  return value?.startsWith("Bearer ") ? value.slice(7).trim() : null;
}

async function authenticatedUserId(db, request) {
  const token = bearer(request);
  if (!token) return null;
  const session = await db
    .prepare("SELECT user_id FROM auth_sessions WHERE token = ? AND expires_at > ?")
    .bind(token, now())
    .first();
  return session?.user_id || null;
}

async function currentUserId(db, request) {
  return (await authenticatedUserId(db, request)) || 1;
}

async function authUser(db, userId) {
  const row = await db
    .prepare(`
      SELECT u.id, u.display_name, u.role, u.avatar, a.email
      FROM users u JOIN user_accounts a ON a.user_id = u.id
      WHERE u.id = ?
    `)
    .bind(userId)
    .first();
  if (!row) return null;
  return {
    id: row.id,
    displayName: row.display_name,
    email: row.email,
    role: row.role,
    avatar: row.avatar,
  };
}

async function createSession(db, userId) {
  const token = randomHex();
  const expiresAt = new Date(Date.now() + SESSION_DAYS * 24 * 60 * 60 * 1000).toISOString();
  await db
    .prepare("INSERT INTO auth_sessions(token, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)")
    .bind(token, userId, expiresAt, now())
    .run();
  return { token, expiresAt, user: await authUser(db, userId) };
}

async function readBody(request) {
  try {
    return await request.json();
  } catch {
    throw new Error("请求内容格式不正确");
  }
}

async function listContents(db, userId, url) {
  const category = url.searchParams.get("category") || "全部";
  const query = (url.searchParams.get("q") || "").trim().toLowerCase();
  const { results } = await db
    .prepare(`
      SELECT c.*, CASE WHEN l.user_id IS NULL THEN 0 ELSE 1 END AS liked
      FROM content_items c
      LEFT JOIN content_likes l ON l.content_id = c.id AND l.user_id = ?
      WHERE (? = '全部' OR c.category = ?)
        AND (? = '' OR LOWER(c.title) LIKE ? OR LOWER(c.author) LIKE ?)
      ORDER BY c.id
    `)
    .bind(userId, category, category, query, `%${query}%`, `%${query}%`)
    .all();
  return results.map(mapContent);
}

async function listTutors(db, url) {
  const subject = url.searchParams.get("subject") || "全部";
  const grade = url.searchParams.get("grade") || "全部";
  const priceRange = url.searchParams.get("priceRange") || "全部";
  const query = (url.searchParams.get("q") || "").trim().toLowerCase();
  const { results } = await db.prepare("SELECT * FROM tutors ORDER BY id").all();
  return results.map(mapTutor).filter((tutor) => {
    const subjectMatch = subject === "全部" || tutor.subjects.some((item) => item.includes(subject));
    const gradeMatch = grade === "全部" || tutor.grades.includes(grade);
    const queryMatch =
      !query || tutor.name.toLowerCase().includes(query) || tutor.school.toLowerCase().includes(query);
    const priceMatch =
      priceRange === "全部" ||
      (priceRange === "<50" && tutor.price < 50) ||
      (priceRange === "50-100" && tutor.price >= 50 && tutor.price <= 100) ||
      (priceRange === "100-150" && tutor.price > 100 && tutor.price <= 150) ||
      (priceRange === ">150" && tutor.price > 150);
    return subjectMatch && gradeMatch && queryMatch && priceMatch;
  });
}

async function handleAuth(request, db, path) {
  if (path === "/api/auth/register" && request.method === "POST") {
    const body = await readBody(request);
    const displayName = String(body.displayName || "").trim();
    const email = String(body.email || "").trim().toLowerCase();
    const password = String(body.password || "");
    const role = String(body.role || "");
    if (displayName.length < 2 || !email.includes("@") || password.length < 6) {
      return json({ message: "请填写有效的昵称、邮箱和至少 6 位密码" }, 400);
    }
    if (!["青少年", "家长", "大学生"].includes(role)) {
      return json({ message: "请选择有效的身份" }, 400);
    }
    const existing = await db.prepare("SELECT id FROM user_accounts WHERE email = ?").bind(email).first();
    if (existing) return json({ message: "该邮箱已注册，请直接登录" }, 400);

    const userResult = await db
      .prepare(`
        INSERT INTO users(
          display_name, role, verified, avatar,
          collections_count, orders_count, communities_count, messages_count
        ) VALUES (?, ?, 0, NULL, 0, 0, 0, 0)
      `)
      .bind(displayName, role)
      .run();
    const userId = userResult.meta.last_row_id;
    await db
      .prepare("INSERT INTO user_accounts(user_id, email, password_hash, created_at) VALUES (?, ?, ?, ?)")
      .bind(userId, email, await hashPassword(password), now())
      .run();
    return json(await createSession(db, userId));
  }

  if (path === "/api/auth/login" && request.method === "POST") {
    const body = await readBody(request);
    const email = String(body.email || "").trim().toLowerCase();
    const account = await db
      .prepare("SELECT user_id, password_hash FROM user_accounts WHERE email = ?")
      .bind(email)
      .first();
    if (!account || !(await passwordMatches(String(body.password || ""), account.password_hash))) {
      return json({ message: "邮箱或密码错误" }, 400);
    }
    return json(await createSession(db, account.user_id));
  }

  if (path === "/api/auth/me" && request.method === "GET") {
    const userId = await authenticatedUserId(db, request);
    if (!userId) return json({ message: "请先登录" }, 401);
    return json(await authUser(db, userId));
  }

  if (path === "/api/auth/logout" && request.method === "POST") {
    const token = bearer(request);
    if (token) await db.prepare("DELETE FROM auth_sessions WHERE token = ?").bind(token).run();
    return json({ active: false, message: "已安全退出" });
  }

  return null;
}

async function handleApi(request, env) {
  if (!env.DB) return json({ message: "云数据库尚未绑定" }, 503);
  const db = env.DB;
  await seedDatabase(db);
  const url = new URL(request.url);
  const path = url.pathname;

  const authResponse = await handleAuth(request, db, path);
  if (authResponse) return authResponse;

  if (path === "/api/home" && request.method === "GET") {
    const userId = await currentUserId(db, request);
    const contents = await listContents(db, userId, new URL(`${url.origin}/api/contents`));
    const tutors = await listTutors(db, new URL(`${url.origin}/api/tutors`));
    return json({
      stats: [
        { label: "粉丝", value: "5万+" },
        { label: "师资", value: "200+" },
        { label: "订单", value: "80+" },
        { label: "服务学生", value: "120+" },
      ],
      contents,
      tutors: tutors.slice(0, 4),
    });
  }

  if (path === "/api/contents" && request.method === "GET") {
    return json(await listContents(db, await currentUserId(db, request), url));
  }

  const likeMatch = path.match(/^\/api\/contents\/(\d+)\/like$/);
  if (likeMatch && request.method === "POST") {
    const userId = await currentUserId(db, request);
    const contentId = Number(likeMatch[1]);
    const existing = await db
      .prepare("SELECT id FROM content_likes WHERE user_id = ? AND content_id = ?")
      .bind(userId, contentId)
      .first();
    if (existing) {
      await db.batch([
        db.prepare("DELETE FROM content_likes WHERE user_id = ? AND content_id = ?").bind(userId, contentId),
        db.prepare("UPDATE content_items SET likes = MAX(likes - 1, 0) WHERE id = ?").bind(contentId),
      ]);
      return json({ active: false, message: "已取消点赞" });
    }
    await db.batch([
      db.prepare("INSERT INTO content_likes(user_id, content_id, created_at) VALUES (?, ?, ?)").bind(userId, contentId, now()),
      db.prepare("UPDATE content_items SET likes = likes + 1 WHERE id = ?").bind(contentId),
    ]);
    return json({ active: true, message: "点赞成功" });
  }

  if (path === "/api/follows" && request.method === "POST") {
    const userId = await currentUserId(db, request);
    const { creatorName } = await readBody(request);
    const existing = await db
      .prepare("SELECT id FROM creator_follows WHERE user_id = ? AND creator_name = ?")
      .bind(userId, creatorName)
      .first();
    if (existing) {
      await db
        .prepare("DELETE FROM creator_follows WHERE user_id = ? AND creator_name = ?")
        .bind(userId, creatorName)
        .run();
      return json({ active: false, message: "已取消关注" });
    }
    await db
      .prepare("INSERT INTO creator_follows(user_id, creator_name, followed_at) VALUES (?, ?, ?)")
      .bind(userId, creatorName, now())
      .run();
    return json({ active: true, message: "关注成功" });
  }

  if (path === "/api/tutors" && request.method === "GET") {
    return json(await listTutors(db, url));
  }

  if (path === "/api/reservations" && request.method === "POST") {
    const userId = await currentUserId(db, request);
    const body = await readBody(request);
    const tutor = await db.prepare("SELECT * FROM tutors WHERE id = ?").bind(body.tutorId).first();
    if (!tutor || !split(tutor.subjects).includes(body.subject)) {
      return json({ message: "请选择该导师擅长的科目" }, 400);
    }
    if (!body.scheduledAt || new Date(body.scheduledAt).getTime() <= Date.now()) {
      return json({ message: "请选择未来的上课时间" }, 400);
    }
    const orderNo = `YZ${Date.now()}${Math.floor(Math.random() * 1000)}`;
    const result = await db
      .prepare(`
        INSERT INTO reservations(order_no, user_id, tutor_id, subject, scheduled_at, status, created_at)
        VALUES (?, ?, ?, ?, ?, '待确认', ?)
      `)
      .bind(orderNo, userId, body.tutorId, body.subject, body.scheduledAt, now())
      .run();
    await db.prepare("UPDATE users SET orders_count = orders_count + 1 WHERE id = ?").bind(userId).run();
    return json(
      {
        id: result.meta.last_row_id,
        orderNo,
        tutorName: tutor.name,
        subject: body.subject,
        scheduledAt: body.scheduledAt,
        status: "待确认",
      },
      201,
    );
  }

  if (path === "/api/communities" && request.method === "GET") {
    const userId = await currentUserId(db, request);
    const { results } = await db
      .prepare(`
        SELECT c.*, CASE WHEN m.user_id IS NULL THEN 0 ELSE 1 END AS joined
        FROM communities c
        LEFT JOIN memberships m ON m.community_id = c.id AND m.user_id = ?
        ORDER BY c.id
      `)
      .bind(userId)
      .all();
    return json(
      results.map((row) => ({
        id: row.id,
        name: row.name,
        description: row.description,
        members: row.members,
        cover: row.cover,
        joined: Boolean(row.joined),
      })),
    );
  }

  const joinMatch = path.match(/^\/api\/communities\/(\d+)\/join$/);
  if (joinMatch && request.method === "POST") {
    const userId = await currentUserId(db, request);
    const communityId = Number(joinMatch[1]);
    const existing = await db
      .prepare("SELECT id FROM memberships WHERE user_id = ? AND community_id = ?")
      .bind(userId, communityId)
      .first();
    if (existing) {
      await db.batch([
        db.prepare("DELETE FROM memberships WHERE user_id = ? AND community_id = ?").bind(userId, communityId),
        db.prepare("UPDATE communities SET members = MAX(members - 1, 0) WHERE id = ?").bind(communityId),
        db.prepare("UPDATE users SET communities_count = MAX(communities_count - 1, 0) WHERE id = ?").bind(userId),
      ]);
      return json({ active: false, message: "已退出社群" });
    }
    await db.batch([
      db.prepare("INSERT INTO memberships(user_id, community_id, joined_at) VALUES (?, ?, ?)").bind(userId, communityId, now()),
      db.prepare("UPDATE communities SET members = members + 1 WHERE id = ?").bind(communityId),
      db.prepare("UPDATE users SET communities_count = communities_count + 1 WHERE id = ?").bind(userId),
    ]);
    return json({ active: true, message: "加入社群成功" });
  }

  if (path === "/api/profile" && request.method === "GET") {
    const row = await db
      .prepare("SELECT * FROM users WHERE id = ?")
      .bind(await currentUserId(db, request))
      .first();
    return json(mapProfile(row));
  }

  if (path === "/api/profile/role" && request.method === "PUT") {
    const userId = await currentUserId(db, request);
    const { role } = await readBody(request);
    if (!["青少年", "家长", "大学生"].includes(role)) {
      return json({ message: "不支持的身份类型" }, 400);
    }
    await db.prepare("UPDATE users SET role = ? WHERE id = ?").bind(role, userId).run();
    return json(mapProfile(await db.prepare("SELECT * FROM users WHERE id = ?").bind(userId).first()));
  }

  if (path === "/api/profile/reservations" && request.method === "GET") {
    const { results } = await db
      .prepare(`
        SELECT r.*, t.name AS tutor_name
        FROM reservations r JOIN tutors t ON t.id = r.tutor_id
        WHERE r.user_id = ?
        ORDER BY r.scheduled_at
      `)
      .bind(await currentUserId(db, request))
      .all();
    return json(
      results.map((row) => ({
        id: row.id,
        orderNo: row.order_no,
        tutorName: row.tutor_name,
        subject: row.subject,
        scheduledAt: row.scheduled_at,
        status: row.status,
      })),
    );
  }

  return json({ message: "接口不存在" }, 404);
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (url.pathname.startsWith("/api/")) {
        return await handleApi(request, env);
      }

      const response = await env.ASSETS.fetch(request);
      const acceptsHtml = request.headers.get("accept")?.includes("text/html");
      if (response.status !== 404 || !acceptsHtml || !["GET", "HEAD"].includes(request.method)) {
        return response;
      }

      const indexUrl = new URL(request.url);
      indexUrl.pathname = "/index.html";
      indexUrl.search = "";
      return env.ASSETS.fetch(new Request(indexUrl, request));
    } catch (error) {
      return json({ message: error instanceof Error ? error.message : "服务暂时不可用" }, 500);
    }
  },
};
