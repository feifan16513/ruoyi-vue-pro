package cn.iocoder.yudao.adminserver.modules.system.service.auth;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomDate;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.addTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import cn.hutool.core.date.DateUtil;
import cn.iocoder.yudao.adminserver.BaseDbAndRedisUnitTest;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.session.SysUserSessionPageReqVO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.auth.SysUserSessionDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.auth.SysUserSessionMapper;
import cn.iocoder.yudao.adminserver.modules.system.dal.mysql.user.SysUserMapper;
import cn.iocoder.yudao.adminserver.modules.system.dal.redis.auth.SysLoginUserRedisDAO;
import cn.iocoder.yudao.adminserver.modules.system.enums.common.SysSexEnum;
import cn.iocoder.yudao.adminserver.modules.system.service.auth.impl.SysUserSessionServiceImpl;
import cn.iocoder.yudao.adminserver.modules.system.service.dept.impl.SysDeptServiceImpl;
import cn.iocoder.yudao.adminserver.modules.system.service.logger.impl.SysLoginLogServiceImpl;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserServiceImpl;
import cn.iocoder.yudao.framework.test.core.util.AssertUtils;
import cn.iocoder.yudao.framework.test.core.util.RandomUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;

/**
 * SysUserSessionServiceImpl Tester.
 *
 * @author Lyon
 * @version 1.0
 * @since <pre>3??? 8, 2021</pre>
 */
@Import({SysUserSessionServiceImpl.class, SysLoginUserRedisDAO.class})
public class SysUserSessionServiceImplTest extends BaseDbAndRedisUnitTest {

    @Resource
    private SysUserSessionServiceImpl sysUserSessionService;
    @Resource
    private SysUserSessionMapper sysUserSessionMapper;
    @Resource
    private SysLoginUserRedisDAO sysLoginUserRedisDAO;
    @Resource
    private SysUserMapper sysUserMapper;

    @MockBean
    private SecurityProperties securityProperties;
    @MockBean
    private SysDeptServiceImpl sysDeptService;
    @MockBean
    private SysUserServiceImpl sysUserService;
    @MockBean
    private SysLoginLogServiceImpl sysLoginLogService;

    @Test
    public void testCreateUserSession_success() {
        // ????????????
        String userIp = randomString();
        String userAgent = randomString();
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock
        when(securityProperties.getSessionTimeout()).thenReturn(Duration.ofDays(1));
        // ??????
        String sessionId = sysUserSessionService.createUserSession(loginUser, userIp, userAgent);
        // ?????????????????????????????????
        SysUserSessionDO sysUserSessionDO = sysUserSessionMapper.selectById(sessionId);
        assertEquals(sysUserSessionDO.getId(), sessionId);
        assertEquals(sysUserSessionDO.getUserId(), loginUser.getId());
        assertEquals(sysUserSessionDO.getUserIp(), userIp);
        assertEquals(sysUserSessionDO.getUserAgent(), userAgent);
        assertEquals(sysUserSessionDO.getUsername(), loginUser.getUsername());
        LoginUser redisLoginUser = sysLoginUserRedisDAO.get(sessionId);
        AssertUtils.assertPojoEquals(redisLoginUser, loginUser, "username","password");
    }

    @Test
    public void testCreateRefreshUserSession_success() {
        // ????????????
        String sessionId = randomString();
        String userIp = randomString();
        String userAgent = randomString();
        Long timeLong = randomLongId();
        String userName = randomString();
        Date date = randomDate();
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock
        when(securityProperties.getSessionTimeout()).thenReturn(Duration.ofDays(1));
        loginUser.setUpdateTime(date);
        sysLoginUserRedisDAO.set(sessionId, loginUser);
        SysUserSessionDO userSession = SysUserSessionDO.builder().id(sessionId)
                .userId(loginUser.getId()).userIp(userIp).userAgent(userAgent).username(userName)
                .sessionTimeout(addTime(Duration.ofMillis(timeLong)))
                .build();
        sysUserSessionMapper.insert(userSession);
        SysUserSessionDO insertDO = sysUserSessionMapper.selectById(sessionId);
        // ??????
        sysUserSessionService.refreshUserSession(sessionId, loginUser);
        // ???????????? redis
        LoginUser redisLoginUser = sysLoginUserRedisDAO.get(sessionId);
        assertNotEquals(redisLoginUser.getUpdateTime(), date);
        // ???????????? SysUserSessionDO
        SysUserSessionDO updateDO = sysUserSessionMapper.selectById(sessionId);
        assertEquals(updateDO.getUsername(), loginUser.getUsername());
        assertNotEquals(updateDO.getUpdateTime(), insertDO.getUpdateTime());
        assertNotEquals(updateDO.getSessionTimeout(), addTime(Duration.ofMillis(timeLong)));
    }

    @Test
    public void testDeleteUserSession_success() {
        // ????????????
        String sessionId = randomString();
        String userIp = randomString();
        String userAgent = randomString();
        Long timeLong = randomLongId();
        LoginUser loginUser = randomPojo(LoginUser.class);
        // mock ?????? Redis
        when(securityProperties.getSessionTimeout()).thenReturn(Duration.ofDays(1));
        sysLoginUserRedisDAO.set(sessionId, loginUser);
        // mock ?????? db
        SysUserSessionDO userSession = SysUserSessionDO.builder().id(sessionId)
                .userId(loginUser.getId()).userIp(userIp).userAgent(userAgent).username(loginUser.getUsername())
                .sessionTimeout(addTime(Duration.ofMillis(timeLong)))
                .build();
        sysUserSessionMapper.insert(userSession);
        // ??????????????????
        assertNotNull(sysLoginUserRedisDAO.get(sessionId));
        assertNotNull(sysUserSessionMapper.selectById(sessionId));
        // ??????
        sysUserSessionService.deleteUserSession(sessionId);
        // ????????????????????????
        assertNull(sysLoginUserRedisDAO.get(sessionId));
        assertNull(sysUserSessionMapper.selectById(sessionId));
    }

    @Test
    public void testGetUserSessionPage_success() {
        // mock ??????
        String userIp = randomString();
        SysUserDO dbUser1 = randomPojo(SysUserDO.class, o -> {
            o.setUsername("testUsername1");
            o.setSex(randomEle(SysSexEnum.values()).getSex());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        SysUserDO dbUser2 = randomPojo(SysUserDO.class, o -> {
            o.setUsername("testUsername2");
            o.setSex(randomEle(SysSexEnum.values()).getSex());
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        SysUserSessionDO dbSession = randomPojo(SysUserSessionDO.class, o -> {
            o.setUserId(dbUser1.getId());
            o.setUserIp(userIp);
        });
        sysUserMapper.insert(dbUser1);
        sysUserMapper.insert(dbUser2);
        sysUserSessionMapper.insert(dbSession);
        sysUserSessionMapper.insert(ObjectUtils.clone(dbSession, o -> {
            o.setId(randomString());
            o.setUserId(dbUser2.getId());
        }));
        // ?????? userId ?????????
        sysUserSessionMapper.insert(ObjectUtils.clone(dbSession, o -> {
            o.setId(randomString());
            o.setUserId(123456l);
        }));
        // ?????? userIp ?????????
        sysUserSessionMapper.insert(ObjectUtils.clone(dbSession, o -> {
            o.setId(randomString());
            o.setUserIp("testUserIp");
        }));
        // ????????????
        SysUserSessionPageReqVO reqVo = new SysUserSessionPageReqVO();
        reqVo.setUserIp(userIp);
        // ??????
        PageResult<SysUserSessionDO> pageResult = sysUserSessionService.getUserSessionPage(reqVo);
        // ??????
        assertEquals(3, pageResult.getTotal());
        assertEquals(3, pageResult.getList().size());
        assertPojoEquals(dbSession, pageResult.getList().get(0));
    }

    @Test
    public void testClearSessionTimeout_success() throws Exception {
        // ?????????????????? 120 ???, ???????????? 1 ???
        int expectedTimeoutCount = 120, expectedTotal = 1;

        // ????????????
        List<SysUserSessionDO> prepareData = Stream
                .iterate(0, i -> i)
                .limit(expectedTimeoutCount)
                .map(i -> RandomUtils.randomPojo(SysUserSessionDO.class, o -> o.setSessionTimeout(DateUtil.offsetSecond(new Date(), -1))))
                .collect(Collectors.toList());
        SysUserSessionDO sessionDO = RandomUtils.randomPojo(SysUserSessionDO.class, o -> o.setSessionTimeout(DateUtil.offsetMinute(new Date(), 30)));
        prepareData.add(sessionDO);
        prepareData.forEach(sysUserSessionMapper::insert);

        //??????????????????
        long actualTimeoutCount = sysUserSessionService.clearSessionTimeout();
        //??????
        assertEquals(expectedTimeoutCount, actualTimeoutCount);
        List<SysUserSessionDO> userSessionDOS = sysUserSessionMapper.selectList();
        assertEquals(expectedTotal, userSessionDOS.size());
        AssertUtils.assertPojoEquals(sessionDO, userSessionDOS.get(0), "updateTime");
    }

}
