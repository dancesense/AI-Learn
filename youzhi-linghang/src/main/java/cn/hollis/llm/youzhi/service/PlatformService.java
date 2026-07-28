package cn.hollis.llm.youzhi.service;

import cn.hollis.llm.youzhi.api.ApiDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class PlatformService {

    private static final long DEMO_USER_ID = 1L;
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;

    public PlatformService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApiDtos.Home home() {
        return new ApiDtos.Home(
                List.of(
                        new ApiDtos.Stat("粉丝", "5万+"),
                        new ApiDtos.Stat("师资", "200+"),
                        new ApiDtos.Stat("订单", "80+"),
                        new ApiDtos.Stat("服务学生", "120+")
                ),
                contents("全部", ""),
                tutors("全部", "全部", "全部", "").stream().limit(4).toList()
        );
    }

    public List<ApiDtos.ContentItem> contents(String category, String query) {
        String normalizedCategory = blankTo(category, "全部");
        String normalizedQuery = blankTo(query, "").toLowerCase(Locale.ROOT);
        return jdbcTemplate.query("""
                        SELECT c.*,
                               CASE WHEN l.user_id IS NULL THEN FALSE ELSE TRUE END AS liked
                        FROM content_items c
                        LEFT JOIN content_likes l ON l.content_id = c.id AND l.user_id = ?
                        WHERE (? = '全部' OR c.category = ?)
                          AND (? = '' OR LOWER(c.title) LIKE ? OR LOWER(c.author) LIKE ?)
                        ORDER BY c.id
                        """,
                (rs, rowNum) -> mapContent(rs),
                DEMO_USER_ID,
                normalizedCategory,
                normalizedCategory,
                normalizedQuery,
                "%" + normalizedQuery + "%",
                "%" + normalizedQuery + "%"
        );
    }

    @Transactional
    public ApiDtos.ActionResult toggleLike(long contentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_likes WHERE user_id = ? AND content_id = ?",
                Integer.class,
                DEMO_USER_ID,
                contentId
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM content_likes WHERE user_id = ? AND content_id = ?", DEMO_USER_ID, contentId);
            jdbcTemplate.update("UPDATE content_items SET likes = GREATEST(likes - 1, 0) WHERE id = ?", contentId);
            return new ApiDtos.ActionResult(false, "已取消点赞");
        }
        jdbcTemplate.update("INSERT INTO content_likes(user_id, content_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", DEMO_USER_ID, contentId);
        jdbcTemplate.update("UPDATE content_items SET likes = likes + 1 WHERE id = ?", contentId);
        return new ApiDtos.ActionResult(true, "点赞成功");
    }

    public List<ApiDtos.Tutor> tutors(String subject, String grade, String priceRange, String query) {
        String subjectValue = blankTo(subject, "全部");
        String gradeValue = blankTo(grade, "全部");
        String priceValue = blankTo(priceRange, "全部");
        String queryValue = blankTo(query, "").toLowerCase(Locale.ROOT);

        return jdbcTemplate.query("SELECT * FROM tutors ORDER BY id", (rs, rowNum) -> mapTutor(rs))
                .stream()
                .filter(tutor -> "全部".equals(subjectValue) || tutor.subjects().stream().anyMatch(item -> item.contains(subjectValue)))
                .filter(tutor -> "全部".equals(gradeValue) || tutor.grades().contains(gradeValue))
                .filter(tutor -> matchesPrice(tutor.price(), priceValue))
                .filter(tutor -> queryValue.isBlank()
                        || tutor.name().toLowerCase(Locale.ROOT).contains(queryValue)
                        || tutor.school().toLowerCase(Locale.ROOT).contains(queryValue))
                .toList();
    }

    public List<ApiDtos.Community> communities() {
        return jdbcTemplate.query("""
                        SELECT c.*,
                               CASE WHEN m.user_id IS NULL THEN FALSE ELSE TRUE END AS joined
                        FROM communities c
                        LEFT JOIN memberships m ON m.community_id = c.id AND m.user_id = ?
                        ORDER BY c.id
                        """,
                (rs, rowNum) -> new ApiDtos.Community(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("members"),
                        rs.getString("cover"),
                        rs.getBoolean("joined")
                ),
                DEMO_USER_ID
        );
    }

    @Transactional
    public ApiDtos.ActionResult toggleCommunity(long communityId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE user_id = ? AND community_id = ?",
                Integer.class,
                DEMO_USER_ID,
                communityId
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM memberships WHERE user_id = ? AND community_id = ?", DEMO_USER_ID, communityId);
            jdbcTemplate.update("UPDATE communities SET members = GREATEST(members - 1, 0) WHERE id = ?", communityId);
            jdbcTemplate.update("UPDATE users SET communities_count = GREATEST(communities_count - 1, 0) WHERE id = ?", DEMO_USER_ID);
            return new ApiDtos.ActionResult(false, "已退出社群");
        }
        jdbcTemplate.update("INSERT INTO memberships(user_id, community_id, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP)", DEMO_USER_ID, communityId);
        jdbcTemplate.update("UPDATE communities SET members = members + 1 WHERE id = ?", communityId);
        jdbcTemplate.update("UPDATE users SET communities_count = communities_count + 1 WHERE id = ?", DEMO_USER_ID);
        return new ApiDtos.ActionResult(true, "加入社群成功");
    }

    @Transactional
    public ApiDtos.ActionResult toggleFollow(String creatorName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM creator_follows WHERE user_id = ? AND creator_name = ?",
                Integer.class,
                DEMO_USER_ID,
                creatorName
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM creator_follows WHERE user_id = ? AND creator_name = ?", DEMO_USER_ID, creatorName);
            return new ApiDtos.ActionResult(false, "已取消关注");
        }
        jdbcTemplate.update("INSERT INTO creator_follows(user_id, creator_name, followed_at) VALUES (?, ?, CURRENT_TIMESTAMP)", DEMO_USER_ID, creatorName);
        return new ApiDtos.ActionResult(true, "关注成功");
    }

    @Transactional
    public ApiDtos.Reservation reserve(ApiDtos.ReservationRequest request) {
        ApiDtos.Tutor tutor = jdbcTemplate.queryForObject(
                "SELECT * FROM tutors WHERE id = ?",
                (rs, rowNum) -> mapTutor(rs),
                request.tutorId()
        );
        if (tutor == null) {
            throw new IllegalArgumentException("未找到对应导师");
        }
        if (!tutor.subjects().contains(request.subject())) {
            throw new IllegalArgumentException("请选择该导师擅长的科目");
        }
        String orderNo = "YZ" + LocalDateTime.now().format(ORDER_TIME);
        jdbcTemplate.update("""
                        INSERT INTO reservations(order_no, user_id, tutor_id, subject, scheduled_at, status, created_at)
                        VALUES (?, ?, ?, ?, ?, '待确认', CURRENT_TIMESTAMP)
                        """,
                orderNo,
                DEMO_USER_ID,
                request.tutorId(),
                request.subject(),
                request.scheduledAt()
        );
        jdbcTemplate.update("UPDATE users SET orders_count = orders_count + 1 WHERE id = ?", DEMO_USER_ID);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM reservations WHERE order_no = ?", Long.class, orderNo);
        return new ApiDtos.Reservation(id == null ? 0 : id, orderNo, tutor.name(), request.subject(), request.scheduledAt(), "待确认");
    }

    public ApiDtos.Profile profile() {
        return profile(DEMO_USER_ID);
    }

    public ApiDtos.Profile profile(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM users WHERE id = ?",
                (rs, rowNum) -> new ApiDtos.Profile(
                        rs.getLong("id"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getBoolean("verified"),
                        rs.getString("avatar"),
                        rs.getInt("collections_count"),
                        rs.getInt("orders_count"),
                        rs.getInt("communities_count"),
                        rs.getInt("messages_count")
                ),
                userId
        );
    }

    @Transactional
    public ApiDtos.Profile updateRole(long userId, String role) {
        if (!List.of("青少年", "家长", "大学生").contains(role)) {
            throw new IllegalArgumentException("不支持的身份类型");
        }
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, userId);
        return profile(userId);
    }

    public List<ApiDtos.Reservation> reservations(long userId) {
        return jdbcTemplate.query("""
                        SELECT r.*, t.name AS tutor_name
                        FROM reservations r
                        JOIN tutors t ON t.id = r.tutor_id
                        WHERE r.user_id = ?
                        ORDER BY r.scheduled_at
                        """,
                (rs, rowNum) -> new ApiDtos.Reservation(
                        rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getString("tutor_name"),
                        rs.getString("subject"),
                        rs.getTimestamp("scheduled_at").toLocalDateTime(),
                        rs.getString("status")
                ),
                userId
        );
    }

    private ApiDtos.ContentItem mapContent(ResultSet rs) throws SQLException {
        return new ApiDtos.ContentItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("views"),
                rs.getString("cover"),
                rs.getString("author"),
                rs.getString("author_avatar"),
                rs.getInt("likes"),
                rs.getInt("comments"),
                rs.getString("category"),
                rs.getBoolean("liked")
        );
    }

    private ApiDtos.Tutor mapTutor(ResultSet rs) throws SQLException {
        return new ApiDtos.Tutor(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("school"),
                split(rs.getString("tags")),
                split(rs.getString("subjects")),
                split(rs.getString("grades")),
                rs.getBigDecimal("price"),
                rs.getString("avatar"),
                rs.getString("description"),
                rs.getBoolean("online")
        );
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split("\\|"));
    }

    private boolean matchesPrice(BigDecimal price, String priceRange) {
        return switch (priceRange) {
            case "<50" -> price.compareTo(BigDecimal.valueOf(50)) < 0;
            case "50-100" -> price.compareTo(BigDecimal.valueOf(50)) >= 0 && price.compareTo(BigDecimal.valueOf(100)) <= 0;
            case "100-150" -> price.compareTo(BigDecimal.valueOf(100)) > 0 && price.compareTo(BigDecimal.valueOf(150)) <= 0;
            case ">150" -> price.compareTo(BigDecimal.valueOf(150)) > 0;
            default -> true;
        };
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
