package cn.hollis.llm.youzhi.api;

import cn.hollis.llm.youzhi.service.PlatformService;
import cn.hollis.llm.youzhi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlatformController {

    private final PlatformService platformService;
    private final AuthService authService;

    public PlatformController(PlatformService platformService, AuthService authService) {
        this.platformService = platformService;
        this.authService = authService;
    }

    @GetMapping("/home")
    public ApiDtos.Home home() {
        return platformService.home();
    }

    @GetMapping("/contents")
    public List<ApiDtos.ContentItem> contents(
            @RequestParam(defaultValue = "全部") String category,
            @RequestParam(defaultValue = "") String q
    ) {
        return platformService.contents(category, q);
    }

    @PostMapping("/contents/{id}/like")
    public ApiDtos.ActionResult toggleLike(@PathVariable long id) {
        return platformService.toggleLike(id);
    }

    @PostMapping("/follows")
    public ApiDtos.ActionResult toggleFollow(@Valid @RequestBody ApiDtos.FollowRequest request) {
        return platformService.toggleFollow(request.creatorName());
    }

    @GetMapping("/tutors")
    public List<ApiDtos.Tutor> tutors(
            @RequestParam(defaultValue = "全部") String subject,
            @RequestParam(defaultValue = "全部") String grade,
            @RequestParam(defaultValue = "全部") String priceRange,
            @RequestParam(defaultValue = "") String q
    ) {
        return platformService.tutors(subject, grade, priceRange, q);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.Reservation reserve(@Valid @RequestBody ApiDtos.ReservationRequest request) {
        return platformService.reserve(request);
    }

    @GetMapping("/communities")
    public List<ApiDtos.Community> communities() {
        return platformService.communities();
    }

    @PostMapping("/communities/{id}/join")
    public ApiDtos.ActionResult toggleCommunity(@PathVariable long id) {
        return platformService.toggleCommunity(id);
    }

    @GetMapping("/profile")
    public ApiDtos.Profile profile(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return platformService.profile(authService.currentUserIdOrDemo(authorization));
    }

    @PutMapping("/profile/role")
    public ApiDtos.Profile updateRole(
            @Valid @RequestBody ApiDtos.RoleUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return platformService.updateRole(authService.currentUserIdOrDemo(authorization), request.role());
    }

    @GetMapping("/profile/reservations")
    public List<ApiDtos.Reservation> reservations(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return platformService.reservations(authService.currentUserIdOrDemo(authorization));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }
}
