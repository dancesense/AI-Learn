package cn.hollis.llm.youzhi.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class DatabaseSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DatabaseSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo-password:}") String demoPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser();
        seedAccount();
        seedContent();
        seedTutors();
        seedCommunities();
        seedReservation();
    }

    private void seedAccount() {
        if (demoPassword.isBlank() || count("user_accounts") > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO user_accounts(user_id, email, password_hash, created_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                1L,
                "demo@youzhi.com",
                passwordEncoder.encode(demoPassword)
        );
    }

    private void seedUser() {
        if (count("users") > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO users(
                            id, display_name, role, verified, avatar,
                            collections_count, orders_count, communities_count, messages_count
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                1L, "游小知", "青少年", true, "/assets/user.jpg", 12, 3, 5, 3
        );
    }

    private void seedContent() {
        if (count("content_items") > 0) {
            return;
        }
        insertContent(1, "如何通过游戏提升逻辑思维？", "1.2万", "/assets/video-1.jpg", "学霸小王", "/assets/avatar-1.jpg", 450, 32, "游戏科普");
        insertContent(2, "清华学长的24小时学习法", "5.8万", "/assets/video-2.jpg", "名校学子", "/assets/avatar-2.jpg", 2300, 156, "学习方法");
        insertContent(3, "初中物理必考知识点总结", "8500", "/assets/video-3.jpg", "物理大咖", "/assets/avatar-3.jpg", 310, 18, "学科知识");
        insertContent(4, "从差生到学霸的逆袭之路", "3.2万", "/assets/video-4.jpg", "励志学姐", "/assets/avatar-4.jpg", 1200, 89, "成长故事");
    }

    private void insertContent(long id, String title, String views, String cover, String author,
                               String authorAvatar, int likes, int comments, String category) {
        jdbcTemplate.update("""
                        INSERT INTO content_items(id, title, views, cover, author, author_avatar, likes, comments, category)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, title, views, cover, author, authorAvatar, likes, comments, category
        );
    }

    private void seedTutors() {
        if (count("tutors") > 0) {
            return;
        }
        insertTutor(1, "张一鸣", "清华大学", "双一流|数学系|3年经验", "初中数学|高中数学", "初中|高中", 150, "/assets/tutor-1.jpg", "擅长启发式教学，让孩子爱上数学。");
        insertTutor(2, "李诗雨", "北京大学", "英语专业|雅思8.0|口语达人", "小学英语|初中英语", "小学|初中", 120, "/assets/tutor-2.jpg", "纯正美式发音，互动式课堂。");
        insertTutor(3, "王博", "复旦大学", "物理竞赛|理综霸主", "高中物理|高中化学", "高中", 180, "/assets/tutor-3.jpg", "深入浅出讲解物理难点。");
        insertTutor(4, "赵悦", "浙江大学", "文综名师|语文满分", "小学语文|初中语文", "小学|初中", 100, "/assets/tutor-4.jpg", "培养阅读兴趣，提升写作能力。");
        insertTutor(5, "陈杰", "上海交通大学", "编程大牛|信息学奥赛", "少儿编程|初中数学", "小学|初中", 130, "/assets/tutor-5.jpg", "逻辑思维训练专家。");
    }

    private void insertTutor(long id, String name, String school, String tags, String subjects,
                             String grades, int price, String avatar, String description) {
        jdbcTemplate.update("""
                        INSERT INTO tutors(id, name, school, tags, subjects, grades, price, avatar, description, online)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, name, school, tags, subjects, grades, BigDecimal.valueOf(price), avatar, description, true
        );
    }

    private void seedCommunities() {
        if (count("communities") > 0) {
            return;
        }
        insertCommunity(1, "数学思维挑战营", "每日一题，挑战思维极限，名校学霸带你刷题。", 1250, "/assets/community-1.jpg");
        insertCommunity(2, "英语口语打卡群", "纯正发音练习，每日语料推送，共同进步。", 890, "/assets/community-2.jpg");
        insertCommunity(3, "高考志愿填报交流", "学长学姐分享报考经验，解析专业前景。", 3400, "/assets/community-3.jpg");
        insertCommunity(4, "青少年编程俱乐部", "Scratch、Python入门到进阶，作品展示台。", 670, "/assets/community-4.jpg");
    }

    private void insertCommunity(long id, String name, String description, int members, String cover) {
        jdbcTemplate.update(
                "INSERT INTO communities(id, name, description, members, cover) VALUES (?, ?, ?, ?, ?)",
                id, name, description, members, cover
        );
    }

    private void seedReservation() {
        if (count("reservations") > 0) {
            return;
        }
        LocalDateTime nextClass = LocalDateTime.now()
                .plusDays(1)
                .withHour(19)
                .withMinute(0)
                .withSecond(0)
                .truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
                        INSERT INTO reservations(id, order_no, user_id, tutor_id, subject, scheduled_at, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                1L, "YZ2026061501", 1L, 1L, "初中数学", nextClass, "进行中"
        );
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
