package cn.hollis.llm.youzhi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loadsHomeData() throws Exception {
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contents.length()").value(4))
                .andExpect(jsonPath("$.tutors.length()").value(4))
                .andExpect(jsonPath("$.stats[0].value").value("5万+"));
    }

    @Test
    void filtersTutors() throws Exception {
        mockMvc.perform(get("/api/tutors").param("subject", "物理"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("王博"));
    }

    @Test
    void createsReservation() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorId": 2,
                                  "subject": "初中英语",
                                  "scheduledAt": "2099-08-01T19:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tutorName").value("李诗雨"))
                .andExpect(jsonPath("$.status").value("待确认"));
    }

    @Test
    void registersAndLogsIn() throws Exception {
        String email = "new-student-" + UUID.randomUUID() + "@example.com";
        String password = "qa-" + UUID.randomUUID();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "新同学",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "青少年"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.displayName").value("新同学"));
    }
}
