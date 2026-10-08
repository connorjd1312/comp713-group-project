package nz.ac.aut.comp713.repair_tickets.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 用模擬的 HTTP 請求測試 API：狀態碼和 JSON 內容是否正確。 */
@SpringBootTest
@Transactional
class TicketControllerTests {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    private static final String VALID_TICKET = """
            {"name":"Wayne","email":"wayne@example.com","phone":"021 123 4567",
             "deviceType":"phone","deviceModel":"iPhone 13","issueDescription":"Cracked screen after a drop"}
            """;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private long createTicket() throws Exception {
        MvcResult result = mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_TICKET))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void createValidTicketReturns201() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(VALID_TICKET))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.deviceType").value("PHONE"));
    }

    @Test
    void invalidFieldsReturn400WithFieldErrors() throws Exception {
        String bad = """
                {"name":"","email":"not-an-email","deviceType":"TOASTER","issueDescription":"short"}
                """;
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.deviceType").exists())
                .andExpect(jsonPath("$.fieldErrors.issueDescription").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        mvc.perform(get("/api/tickets/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mvc.perform(get("/api/tickets").param("status", "BROKEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    @Test
    void validStatusUpdateReturns200WithHistory() throws Exception {
        long id = createTicket();
        mvc.perform(patch("/api/tickets/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DIAGNOSING\",\"note\":\"Checking screen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DIAGNOSING"))
                .andExpect(jsonPath("$.history.length()").value(1));
    }

    @Test
    void skippingStatusReturns409() throws Exception {
        long id = createTicket();
        mvc.perform(patch("/api/tickets/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
    }

    @Test
    void cancelReturns204ThenSecondCancelReturns409() throws Exception {
        long id = createTicket();
        mvc.perform(delete("/api/tickets/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/tickets/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CANNOT_CANCEL"));
    }
}
