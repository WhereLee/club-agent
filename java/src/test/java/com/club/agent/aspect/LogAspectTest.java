package com.club.agent.aspect;

import com.club.agent.entity.OperLog;
import com.club.agent.service.OperLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogAspect 脱敏行为（S1 回归）：password/secret 字段（含嵌套 DTO）序列化落库时值必须为 ******。
 * 纯 Mockito：真实 ObjectMapper（脱敏依赖真实序列化），mock OperLogService。
 */
class LogAspectTest {

    @Data
    static class RegisterDTO {
        private String username;
        private String password;
    }

    @Data
    static class ChangePasswordDTO {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    static class NestedDTO {
        private RegisterDTO inner;
        private String note;
    }

    @Test
    @DisplayName("register DTO：password 脱敏为 ******")
    void register_password_masked() throws Throwable {
        OperLogService logService = mock(OperLogService.class);
        LogAspect aspect = new LogAspect(logService, new ObjectMapper());

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("stu_x");
        dto.setPassword("PlainText123");

        ProceedingJoinPoint pjp = mockJoinPoint(new Object[]{dto});

        aspect.around(pjp, mockLog("用户注册"));

        ArgumentCaptor<OperLog> captor = ArgumentCaptor.forClass(OperLog.class);
        verify(logService).saveAsync(captor.capture());
        String params = captor.getValue().getParams();
        assertFalse(params.contains("PlainText123"), "params 不得含明文密码: " + params);
        assertTrue(params.contains("******"), "params 应含掩码值: " + params);
        assertTrue(params.contains("stu_x"), "非敏感字段不受影响: " + params);
    }

    @Test
    @DisplayName("改密 DTO：oldPassword/newPassword 均脱敏")
    void changePassword_both_masked() throws Throwable {
        OperLogService logService = mock(OperLogService.class);
        LogAspect aspect = new LogAspect(logService, new ObjectMapper());

        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("OldPass1");
        dto.setNewPassword("NewPass2");

        ProceedingJoinPoint pjp = mockJoinPoint(new Object[]{dto});

        aspect.around(pjp, mockLog("修改密码"));

        ArgumentCaptor<OperLog> captor = ArgumentCaptor.forClass(OperLog.class);
        verify(logService).saveAsync(captor.capture());
        String params = captor.getValue().getParams();
        assertFalse(params.contains("OldPass1"), "oldPassword 不得明文: " + params);
        assertFalse(params.contains("NewPass2"), "newPassword 不得明文: " + params);
    }

    @Test
    @DisplayName("嵌套 DTO：递归脱敏同样生效")
    void nested_dto_masked() throws Throwable {
        OperLogService logService = mock(OperLogService.class);
        LogAspect aspect = new LogAspect(logService, new ObjectMapper());

        RegisterDTO inner = new RegisterDTO();
        inner.setUsername("inner_user");
        inner.setPassword("NestedSecret");
        NestedDTO dto = new NestedDTO();
        dto.setInner(inner);
        dto.setNote("普通说明");

        ProceedingJoinPoint pjp = mockJoinPoint(new Object[]{dto});

        aspect.around(pjp, mockLog("嵌套接口"));

        ArgumentCaptor<OperLog> captor = ArgumentCaptor.forClass(OperLog.class);
        verify(logService).saveAsync(captor.capture());
        String params = captor.getValue().getParams();
        assertFalse(params.contains("NestedSecret"), "嵌套 password 不得明文: " + params);
        assertTrue(params.contains("inner_user"), "嵌套非敏感字段保留: " + params);
    }

    private ProceedingJoinPoint mockJoinPoint(Object[] args) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.toLongString()).thenReturn("test.LogAspectTest.around()");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn(null);
        return pjp;
    }

    private com.club.agent.annotation.Log mockLog(String operation) {
        com.club.agent.annotation.Log logAnno = mock(com.club.agent.annotation.Log.class);
        when(logAnno.module()).thenReturn("测试");
        when(logAnno.operation()).thenReturn(operation);
        return logAnno;
    }
}
