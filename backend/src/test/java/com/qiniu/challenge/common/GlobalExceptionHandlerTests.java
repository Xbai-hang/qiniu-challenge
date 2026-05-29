package com.qiniu.challenge.common;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTests.TestErrorController.class)
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsUnifiedNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("资源不存在"))
                .andExpect(jsonPath("$.error.details", notNullValue()))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void returnsUnifiedBusinessErrorResponse() throws Exception {
        mockMvc.perform(post("/api/test-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("该时间段存在日程冲突"))
                .andExpect(jsonPath("$.error.details.field").value("startTime"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void returnsUnifiedValidationErrorResponse() throws Exception {
        mockMvc.perform(post("/api/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.error.details.fields[0].field").value("name"))
                .andExpect(jsonPath("$.error.details.fields[0].message", notNullValue()))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @RestController
    @RequestMapping("/api/test-errors")
    static class TestErrorController {

        @PostMapping("/conflict")
        void conflict() {
            throw new ApiException(ErrorCode.CONFLICT, "该时间段存在日程冲突", java.util.Map.of("field", "startTime"));
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(
            @NotBlank String name
    ) {
    }
}
