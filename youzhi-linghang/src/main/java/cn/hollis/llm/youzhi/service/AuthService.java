package cn.hollis.llm.youzhi.service;

import cn.hollis.llm.youzhi.api.ApiDtos;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final long DEMO_USER_ID = 1L;
    private static final int SESSION_DAYS = 7;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ApiDtos.AuthResponse register(ApiDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (!List.of("青少年", "家长", "大学生").contains(request.role())) {
            throw new IllegalArgumentException("请选择有效的身份");
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_accounts WHERE email = ?",
                Integer.class,
                email
        );
        if (existing != null && existing > 0) {
            throw new IllegalArgumentException("该邮箱已注册，请直接登录");
        }

        jdbcTemplate.update("""
                        INSERT INTO users(
                            display_name, role, verified, avatar,
                            collections_count, orders_count, communities_count, messages_count
                        ) VALUES (?, ?, FALSE, NULL, 0, 0, 0, 0)
                        """,
                request.displayName().trim(),
                request.role()
        );
        Long userId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM users", Long.class);
        if (userId == null) {
            throw new IllegalStateException("注册失败，请稍后重试");
        }
        jdbcTemplate.update(
                "INSERT INTO user_accounts(user_id, email, password_hash, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                userId,
                email,
                passwordEncoder.encode(request.password())
        );
        return createSession(userId);
    }

    @Transactional
    public ApiDtos.AuthResponse login(ApiDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        List<AccountRow> accounts = jdbcTemplate.query("""
                        SELECT a.user_id, a.password_hash
                        FROM user_accounts a
                        WHERE a.email = ?
                        """,
                (rs, rowNum) -> new AccountRow(rs.getLong("user_id"), rs.getString("password_hash")),
                email
        );
        if (accounts.isEmpty() || !passwordEncoder.matches(request.password(), accounts.getFirst().passwordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        return createSession(accounts.getFirst().userId());
    }

    @Transactional
    public ApiDtos.ActionResult logout(String authorization) {
        String token = bearerToken(authorization);
        if (token != null) {
            jdbcTemplate.update("DELETE FROM auth_sessions WHERE token = ?", token);
        }
        return new ApiDtos.ActionResult(false, "已安全退出");
    }

    public ApiDtos.AuthUser me(String authorization) {
        Long userId = authenticatedUserId(authorization)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
        return loadUser(userId);
    }

    public long currentUserIdOrDemo(String authorization) {
        return authenticatedUserId(authorization).orElse(DEMO_USER_ID);
    }

    private ApiDtos.AuthResponse createSession(long userId) {
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE expires_at <= CURRENT_TIMESTAMP");
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(SESSION_DAYS);
        jdbcTemplate.update(
                "INSERT INTO auth_sessions(token, user_id, expires_at, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                token,
                userId,
                expiresAt
        );
        return new ApiDtos.AuthResponse(token, expiresAt, loadUser(userId));
    }

    private ApiDtos.AuthUser loadUser(long userId) {
        return jdbcTemplate.queryForObject("""
                        SELECT u.id, u.display_name, a.email, u.role, u.avatar
                        FROM users u
                        JOIN user_accounts a ON a.user_id = u.id
                        WHERE u.id = ?
                        """,
                (rs, rowNum) -> new ApiDtos.AuthUser(
                        rs.getLong("id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("avatar")
                ),
                userId
        );
    }

    private Optional<Long> authenticatedUserId(String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            return Optional.empty();
        }
        List<Long> ids = jdbcTemplate.query("""
                        SELECT user_id
                        FROM auth_sessions
                        WHERE token = ? AND expires_at > CURRENT_TIMESTAMP
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                token
        );
        return ids.stream().findFirst();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record AccountRow(long userId, String passwordHash) {
    }
}
