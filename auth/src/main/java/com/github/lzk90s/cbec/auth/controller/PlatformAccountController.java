package com.github.lzk90s.cbec.auth.controller;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.github.lzk90s.cbec.auth.dao.entity.PlatformAccountEntity;
import com.github.lzk90s.cbec.auth.service.PlatformAccountService;
import com.github.lzk90s.cbec.common.rest.Result;
import com.github.lzk90s.cbec.common.util.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platform_account")
public class PlatformAccountController {
    @Autowired
    private PlatformAccountService platformAccountService;

    @PostMapping
    public Result<Void> addAccount(@RequestBody PlatformAccountEntity platformAccountEntity) {
        platformAccountEntity.setUser(UserUtil.getUserName());
        platformAccountService.insert(platformAccountEntity);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> updateAccount(@RequestBody PlatformAccountEntity platformAccountEntity) {
        platformAccountEntity.setUser(UserUtil.getUserName());
        platformAccountService.insertOrUpdate(platformAccountEntity);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        platformAccountService.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<PlatformAccountEntity> getAccount(@PathVariable Long id) {
        return Result.ok(platformAccountService.selectById(id));
    }

    @GetMapping
    public Result<List<PlatformAccountEntity>> listAccount() {
        var entityWrapper = new EntityWrapper<PlatformAccountEntity>()
                .eq("user", UserUtil.getUserName());
        var list = platformAccountService.selectList(entityWrapper);
        if (CollectionUtils.isEmpty(list)) {
            return Result.ok();
        }
        return Result.ok(list).total(platformAccountService.selectCount(entityWrapper));
    }
}