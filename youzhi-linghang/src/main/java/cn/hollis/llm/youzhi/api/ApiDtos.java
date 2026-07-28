package cn.hollis.llm.youzhi.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record ContentItem(
            long id,
            String title,
            String views,
            String cover,
            String author,
            String authorAvatar,
            int likes,
            int comments,
            String category,
            boolean liked
    ) {
    }

    public record Tutor(
            long id,
            String name,
            String school,
            List<String> tags,
            List<String> subjects,
            List<String> grades,
            BigDecimal price,
            String avatar,
            String description,
            boolean online
    ) {
    }

    public record Community(
            long id,
            String name,
            String description,
            int members,
            String cover,
            boolean joined
    ) {
    }

    public record Profile(
            long id,
            String displayName,
            String role,
            boolean verified,
            String avatar,
            int collections,
            int orders,
            int communities,
            int messages
    ) {
    }

    public record Home(
            List<Stat> stats,
            List<ContentItem> contents,
            List<Tutor> tutors
    ) {
    }

    public record Stat(String label, String value) {
    }

    public record ReservationRequest(
            @NotNull Long tutorId,
            @NotBlank String subject,
            @NotNull @Future LocalDateTime scheduledAt
    ) {
    }

    public record Reservation(
            long id,
            String orderNo,
            String tutorName,
            String subject,
            LocalDateTime scheduledAt,
            String status
    ) {
    }

    public record RoleUpdateRequest(@NotBlank String role) {
    }

    public record FollowRequest(@NotBlank String creatorName) {
    }

    public record ActionResult(boolean active, String message) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 30) String displayName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank String role
    ) {
    }

    public record AuthUser(
            long id,
            String displayName,
            String email,
            String role,
            String avatar
    ) {
    }

    public record AuthResponse(
            String token,
            LocalDateTime expiresAt,
            AuthUser user
    ) {
    }
}
