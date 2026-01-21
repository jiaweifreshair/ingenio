package com.ingenio.backend.langchain4j;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FrontendErrorParser单元测试
 */
@SpringBootTest
class FrontendErrorParserTest {

    private final FrontendErrorParser parser = new FrontendErrorParser();

    @Test
    void testParseBabelUnterminatedStringError() {
        // 模拟真实的Babel错误输出
        String errorOutput = """
                [plugin:vite:react-babel] /home/user/app/src/pages/TeacherLogin.jsx: Unterminated string constant. (7:36)
                5  |
                6  |  export default function TeacherLogin() {
                7  |   const [email, setEmail] = useState(')
                   |                                      ^
                8  |   const [password, setPassword] = useState(')
                9  |   const [error, setError] = useState(')
                """;

        FrontendErrorParser.ParsedError result = parser.parse(errorOutput);

        assertNotNull(result);
        assertEquals("BABEL_SYNTAX_ERROR", result.getErrorType());
        assertEquals("src/pages/TeacherLogin.jsx", result.getFilePath());
        assertEquals(7, result.getLine());
        assertEquals(36, result.getColumn());
        assertEquals("Unterminated string constant.", result.getMessage());
    }

    @Test
    void testParseInvalidInput() {
        FrontendErrorParser.ParsedError result = parser.parse("Invalid error message");
        assertNull(result);
    }

    @Test
    void testParseNullInput() {
        FrontendErrorParser.ParsedError result = parser.parse(null);
        assertNull(result);
    }
}
