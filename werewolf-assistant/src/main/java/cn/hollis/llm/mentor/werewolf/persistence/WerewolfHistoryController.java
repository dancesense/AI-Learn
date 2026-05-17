package cn.hollis.llm.mentor.werewolf.persistence;

import cn.hollis.llm.mentor.werewolf.persistence.dto.CloseGameRequest;
import cn.hollis.llm.mentor.werewolf.persistence.dto.CreateGameRequest;
import cn.hollis.llm.mentor.werewolf.persistence.dto.WerewolfGameResponse;
import cn.hollis.llm.mentor.werewolf.persistence.dto.WerewolfSnapshotResponse;
import cn.hollis.llm.mentor.werewolf.persistence.dto.WerewolfSnapshotSaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/werewolf/history")
public class WerewolfHistoryController {

    private final WerewolfHistoryService werewolfHistoryService;

    public WerewolfHistoryController(WerewolfHistoryService werewolfHistoryService) {
        this.werewolfHistoryService = werewolfHistoryService;
    }

    @PostMapping("/games")
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest request) {
        try {
            return ResponseEntity.ok(werewolfHistoryService.createGame(request));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/games")
    public Page<WerewolfGameResponse> listGames(@PageableDefault(size = 20) Pageable pageable) {
        return werewolfHistoryService.listGames(pageable);
    }

    @GetMapping("/games/{gameId}")
    public ResponseEntity<?> getGame(@PathVariable Long gameId) {
        try {
            return ResponseEntity.ok(werewolfHistoryService.getGame(gameId));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/games/{gameId}/snapshots")
    public ResponseEntity<?> saveSnapshot(@PathVariable Long gameId, @RequestBody WerewolfSnapshotSaveRequest request) {
        try {
            return ResponseEntity.ok(werewolfHistoryService.saveSnapshot(gameId, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/games/{gameId}/snapshots")
    public ResponseEntity<?> listSnapshots(@PathVariable Long gameId) {
        try {
            List<WerewolfSnapshotResponse> list = werewolfHistoryService.listSnapshots(gameId);
            return ResponseEntity.ok(list);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/games/{gameId}/close")
    public ResponseEntity<?> closeGame(@PathVariable Long gameId, @RequestBody(required = false) CloseGameRequest request) {
        try {
            return ResponseEntity.ok(werewolfHistoryService.closeGame(gameId, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            return ResponseEntity.ok(werewolfHistoryService.getStats());
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("totalGames", 0, "winRate", 0));
        }
    }
}
