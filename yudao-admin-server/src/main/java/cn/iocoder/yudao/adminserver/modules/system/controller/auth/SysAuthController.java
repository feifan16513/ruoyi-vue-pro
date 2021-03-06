package cn.iocoder.yudao.adminserver.modules.system.controller.auth;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.operatelog.core.annotations.OperateLog;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthLoginReqVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthLoginRespVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthMenuRespVO;
import cn.iocoder.yudao.adminserver.modules.system.controller.auth.vo.auth.SysAuthPermissionInfoRespVO;
import cn.iocoder.yudao.adminserver.modules.system.convert.auth.SysAuthConvert;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysMenuDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.permission.SysRoleDO;
import cn.iocoder.yudao.adminserver.modules.system.dal.dataobject.user.SysUserDO;
import cn.iocoder.yudao.adminserver.modules.system.enums.permission.MenuTypeEnum;
import cn.iocoder.yudao.adminserver.modules.system.service.auth.SysAuthService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysPermissionService;
import cn.iocoder.yudao.adminserver.modules.system.service.permission.SysRoleService;
import cn.iocoder.yudao.adminserver.modules.system.service.user.SysUserService;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserRoleIds;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getUserAgent;

@Api(tags = "??????")
@RestController
@RequestMapping("/")
@Validated
public class SysAuthController {

    @Resource
    private SysAuthService authService;
    @Resource
    private SysUserService userService;
    @Resource
    private SysRoleService roleService;
    @Resource
    private SysPermissionService permissionService;

    @PostMapping("/login")
    @ApiOperation("????????????????????????")
    @OperateLog(enable = false) // ?????? Post ???????????????????????????
    public CommonResult<SysAuthLoginRespVO> login(@RequestBody @Valid SysAuthLoginReqVO reqVO) {
        String token = authService.login(reqVO, getClientIP(), getUserAgent());
        // ????????????
        return success(SysAuthLoginRespVO.builder().token(token).build());
    }

    @GetMapping("/get-permission-info")
    @ApiOperation("?????????????????????????????????")
    public CommonResult<SysAuthPermissionInfoRespVO> getPermissionInfo() {
        // ??????????????????
        SysUserDO user = userService.getUser(getLoginUserId());
        if (user == null) {
            return null;
        }
        // ??????????????????
        List<SysRoleDO> roleList = roleService.getRolesFromCache(getLoginUserRoleIds());
        // ??????????????????
        List<SysMenuDO> menuList = permissionService.getRoleMenusFromCache(
                getLoginUserRoleIds(), // ???????????????????????????????????????????????????????????????????????????
                SetUtils.asSet(MenuTypeEnum.DIR.getType(), MenuTypeEnum.MENU.getType(), MenuTypeEnum.BUTTON.getType()),
                SetUtils.asSet(CommonStatusEnum.ENABLE.getStatus()));
        // ??????????????????
        return success(SysAuthConvert.INSTANCE.convert(user, roleList, menuList));
    }

    @GetMapping("list-menus")
    @ApiOperation("?????????????????????????????????")
    public CommonResult<List<SysAuthMenuRespVO>> getMenus() {
        // ?????????????????????????????????
        List<SysMenuDO> menuList = permissionService.getRoleMenusFromCache(
                getLoginUserRoleIds(), // ???????????????????????????????????????????????????????????????????????????
                SetUtils.asSet(MenuTypeEnum.DIR.getType(), MenuTypeEnum.MENU.getType()), // ???????????????????????????
                SetUtils.asSet(CommonStatusEnum.ENABLE.getStatus())); // ???????????????
        // ????????? Tree ????????????
        return success(SysAuthConvert.INSTANCE.buildMenuTree(menuList));
    }

}
