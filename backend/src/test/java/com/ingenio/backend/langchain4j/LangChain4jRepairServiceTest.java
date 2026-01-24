package com.ingenio.backend.langchain4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LangChain4jRepairService单元测试
 */
@ExtendWith(MockitoExtension.class)
class LangChain4jRepairServiceTest {

    @Mock
    private FrontendErrorParser frontendErrorParser;

    private LangChain4jRepairService repairService;

    @BeforeEach
    void setUp() {
        repairService = new LangChain4jRepairService();
        ReflectionTestUtils.setField(repairService, "baseUrl", "https://api.qnaigc.com");
        ReflectionTestUtils.setField(repairService, "apiKey", "test-key");
        ReflectionTestUtils.setField(repairService, "model", "deepseek-v3");
        ReflectionTestUtils.setField(repairService, "frontendErrorParser", frontendErrorParser);
    }

    @Test
    void testAutoRepairWithUnparsableError() {
        String errorOutput = "Invalid error";
        String fileContent = "const x = 1;";

        when(frontendErrorParser.parse(errorOutput)).thenReturn(null);

        String result = repairService.autoRepairFrontendError(errorOutput, fileContent);

        assertNull(result);
        verify(frontendErrorParser).parse(errorOutput);
    }
}
