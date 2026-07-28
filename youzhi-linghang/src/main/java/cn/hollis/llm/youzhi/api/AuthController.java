package cn.hollis.llm.youzhi.api;

import cn.hollis.llm.youzhi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiDtos.AuthResponse register(@Valid @RequestBody ApiDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ApiDtos.AuthResponse login(@Valid @RequestBody ApiDtos.LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public ApiDtos.AuthUser me(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return authService.me(authorization);
    }

    @PostMapping("/logout")
    public ApiDtos.ActionResult logout(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return authService.logout(authorization);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }
}
