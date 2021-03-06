package cn.iocoder.yudao.adminserver.modules.system.service.auth.impl;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthLoginReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.logger.vo.loginlog.SysLoginLogCreateReqVO;
import cn.iocoder.yudao.adminserver.modules.system.convert.auth.SysAuthConvert;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.adminserver.modules.system.enums.logger.SysLoginLogTypeEnum;
import cn.iocoder.yudao.adminserver.modules.system.enums.logger.SysLoginResultEnum;
import cn.iocoder.yudao.adminserver.modules.system.service.auth.SysAuthService;
import cn.iocoder.yudao.adminserver.modules.system.service.auth.SysUserSessionService;
import cn.iocoder.yudao.adminserver.modules.system.service.common.SysCaptchaService;
import cn.iocoder.yudao.adminserver.modules.system.service.logger.SysLoginLogService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysPermissionService;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserService;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.adminserver.modules.system.enums.SysErrorCodeConstants.*;
import static java.util.Collections.singleton;

/**
 * Auth Service ?????????
 *
 * @author ????????????
 */
@Service
@Slf4j
public class SysAuthServiceImpl implements SysAuthService {

    @Resource
    @Lazy // ????????????????????????????????????????????????
    private AuthenticationManager authenticationManager;
    @Resource
    private SysUserService userService;
    @Resource
    private SysPermissionService permissionService;
    @Resource
    private SysCaptchaService captchaService;
    @Resource
    private SysLoginLogService loginLogService;
    @Resource
    private SysUserSessionService userSessionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ?????? username ????????? SysUserDO
        SysUserDO user = userService.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException(username);
        }
        // ?????? LoginUser ??????
        return SysAuthConvert.INSTANCE.convert(user);
    }

    @Override
    public LoginUser mockLogin(Long userId) {
        // ??????????????????????????? SysUserDO
        SysUserDO user = userService.getUser(userId);
        if (user == null) {
            throw new UsernameNotFoundException(String.valueOf(userId));
        }
        // ?????? LoginUser ??????
        LoginUser loginUser = SysAuthConvert.INSTANCE.convert(user);
        loginUser.setRoleIds(this.getUserRoleIds(loginUser.getId())); // ????????????????????????
        return loginUser;
    }

    @Override
    public String login(SysAuthLoginReqVO reqVO, String userIp, String userAgent) {
        // ???????????????????????????
        this.verifyCaptcha(reqVO.getUsername(), reqVO.getUuid(), reqVO.getCode());

        // ????????????????????????????????????
        LoginUser loginUser = this.login0(reqVO.getUsername(), reqVO.getPassword());
        loginUser.setRoleIds(this.getUserRoleIds(loginUser.getId())); // ????????????????????????

        // ????????????????????? Redis ???????????? sessionId ??????
        return userSessionService.createUserSession(loginUser, userIp, userAgent);
    }

    private void verifyCaptcha(String username, String captchaUUID, String captchaCode) {
        String code = captchaService.getCaptchaCode(captchaUUID);
        // ??????????????????
        if (code == null) {
            // ????????????????????????????????????????????????
            this.createLoginLog(username, SysLoginResultEnum.CAPTCHA_NOT_FOUND);
            throw ServiceExceptionUtil.exception(AUTH_LOGIN_CAPTCHA_NOT_FOUND);
        }
        // ??????????????????
        if (!code.equals(captchaCode)) {
            // ?????????????????????????????????????????????)
            this.createLoginLog(username, SysLoginResultEnum.CAPTCHA_CODE_ERROR);
            throw ServiceExceptionUtil.exception(AUTH_LOGIN_CAPTCHA_CODE_ERROR);
        }
        // ????????????????????????????????????
        captchaService.deleteCaptchaCode(captchaUUID);
    }

    private LoginUser login0(String username, String password) {
        // ????????????
        Authentication authentication;
        try {
            // ?????? Spring Security ??? AuthenticationManager#authenticate(...) ???????????????????????????????????????
            // ??????????????????????????? loadUserByUsername ??????????????? User ??????
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException badCredentialsException) {
            this.createLoginLog(username, SysLoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        } catch (DisabledException disabledException) {
            this.createLoginLog(username, SysLoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        } catch (AuthenticationException authenticationException) {
            log.error("[login0][username({}) ??????????????????]", username, authenticationException);
            this.createLoginLog(username, SysLoginResultEnum.UNKNOWN_ERROR);
            throw exception(AUTH_LOGIN_FAIL_UNKNOWN);
        }
        // ????????????
        Assert.notNull(authentication.getPrincipal(), "Principal ????????????");
        this.createLoginLog(username, SysLoginResultEnum.SUCCESS);
        return (LoginUser) authentication.getPrincipal();
    }

    private void createLoginLog(String username, SysLoginResultEnum loginResult) {
        SysLoginLogCreateReqVO reqVO = new SysLoginLogCreateReqVO();
        reqVO.setLogType(SysLoginLogTypeEnum.LOGIN_USERNAME.getType());
        reqVO.setTraceId(TracerUtils.getTraceId());
        reqVO.setUsername(username);
        reqVO.setUserAgent(ServletUtils.getUserAgent());
        reqVO.setUserIp(ServletUtils.getClientIP());
        reqVO.setResult(loginResult.getResult());
        loginLogService.createLoginLog(reqVO);
    }

    /**
     * ?????? User ???????????????????????????
     *
     * @param userId ????????????
     * @return ??????????????????
     */
    private Set<Long> getUserRoleIds(Long userId) {
        return permissionService.getUserRoleIds(userId, singleton(CommonStatusEnum.ENABLE.getStatus()));
    }

    @Override
    public void logout(String token) {
        // ??????????????????
        LoginUser loginUser = userSessionService.getLoginUser(token);
        if (loginUser == null) {
            return;
        }
        // ?????? session
        userSessionService.deleteUserSession(token);
        // ?????????????????????
        this.createLogoutLog(loginUser.getUsername());
    }

    private void createLogoutLog(String username) {
        SysLoginLogCreateReqVO reqVO = new SysLoginLogCreateReqVO();
        reqVO.setLogType(SysLoginLogTypeEnum.LOGOUT_SELF.getType());
        reqVO.setTraceId(TracerUtils.getTraceId());
        reqVO.setUsername(username);
        reqVO.setUserAgent(ServletUtils.getUserAgent());
        reqVO.setUserIp(ServletUtils.getClientIP());
        reqVO.setResult(SysLoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqVO);
    }

    @Override
    public LoginUser verifyTokenAndRefresh(String token) {
        // ?????? LoginUser
        LoginUser loginUser = userSessionService.getLoginUser(token);
        if (loginUser == null) {
            return null;
        }
        // ?????? LoginUser ??????
        this.refreshLoginUserCache(token, loginUser);
        return loginUser;
    }

    private void refreshLoginUserCache(String token, LoginUser loginUser) {
        // ??? 1/3 ??? Session ????????????????????? LoginUser ??????
        if (System.currentTimeMillis() - loginUser.getUpdateTime().getTime() <
                userSessionService.getSessionTimeoutMillis() / 3) {
            return;
        }

        // ???????????? SysUserDO ??????
        SysUserDO user = userService.getUser(loginUser.getId());
        if (user == null || CommonStatusEnum.DISABLE.getStatus().equals(user.getStatus())) {
            throw exception(TOKEN_EXPIRED); // ?????? token ????????????????????????????????????????????? token ??????????????????????????????????????????
        }

        // ?????? LoginUser ??????
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoleIds(this.getUserRoleIds(loginUser.getId()));
        userSessionService.refreshUserSession(token, loginUser);
    }

}
