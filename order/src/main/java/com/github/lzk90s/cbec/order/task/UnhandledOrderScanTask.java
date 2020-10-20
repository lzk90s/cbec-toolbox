package com.github.lzk90s.cbec.order.task;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.github.lzk90s.cbec.internal.api.auth.PlatformAccountDTO;
import com.github.lzk90s.cbec.internal.api.messager.MessageDTO;
import com.github.lzk90s.cbec.internal.api.spider.OrderDTO;
import com.github.lzk90s.cbec.order.dao.entity.OrderEntity;
import com.github.lzk90s.cbec.order.feign.MessageApiFeign;
import com.github.lzk90s.cbec.order.feign.OrderSpiderApiFeign;
import com.github.lzk90s.cbec.order.feign.PlatformAccountApiFeign;
import com.github.lzk90s.cbec.order.feign.UserApiFeign;
import com.github.lzk90s.cbec.order.service.OrderService;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfig;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/tasks/unhandled_order_scan_task")
@Component
public class UnhandledOrderScanTask {
    @Autowired
    private PlatformAccountApiFeign platformAccountApiFeign;

    @Autowired
    private UserApiFeign userApiFeign;

    @Autowired
    private OrderSpiderApiFeign orderSpiderApiFeign;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MessageApiFeign messageApiFeign;

    @Autowired
    private FreeMarkerConfig freeMarkerConfig;

    private String templateName = "unhandled-order.html";

    @Value("${notify.internalHour:1}")
    private int notifyInternalHour;

    @Transactional(rollbackFor = Exception.class)
    @GetMapping("/execute")
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void scan() {
        log.info("Start scan......");
        var accountList = platformAccountApiFeign.listAllUserPlatformAccount();
        if (CollectionUtils.isEmpty(accountList)) {
            log.info("No account, skip");
            return;
        }

        accountList.forEach(account -> {
            syncUserUnhandledOrder(account);
            notifyUserNewOrder(account.getUserName());
        });
    }

    private void syncUserUnhandledOrder(PlatformAccountDTO account) {
        var orderList = orderSpiderApiFeign.listUnhandledOrder(account.getPlatform(),
                account.getUser(), account.getPassword());
        if (CollectionUtils.isEmpty(orderList)) {
            return;
        }

        // 删除不存在的订单
        var orderIdList = orderList.stream().map(OrderDTO::getId).collect(Collectors.toList());
        orderService.delete(new EntityWrapper<OrderEntity>().notIn("id", orderIdList));

        var orderEntityList = orderList.stream()
                .map(s -> OrderEntity.getConverter().doBackward(s))
                .peek(s -> s.setPlatform(account.getPlatform()))
                .peek(s -> s.setUser(account.getUserName()))
                .collect(Collectors.toList());
        orderService.insertOrUpdateBatch(orderEntityList);

        log.info("发现用户{}的{}个未处理订单", account.getUserName(), orderEntityList.size());
    }

    private void notifyUserNewOrder(String user) {
        // 查询没有通知过，或者已经过期的订单
        var orderList = orderService.selectList(new EntityWrapper<OrderEntity>()
                .eq("user", user).le("last_notify_time", getTimeBeforeNow(notifyInternalHour))
                .or().isNull("last_notify_time"));
        if (CollectionUtils.isEmpty(orderList)) {
            return;
        }

        var userInfo = userApiFeign.getUserInfo(user);
        var messageDTO = new MessageDTO(userInfo.getEmail(), buildNotifyMessage(orderList));
        messageApiFeign.sendMail(messageDTO);

        log.info("通知用户{}有{}个未处理的订单", user, orderList.size());

        // 更新最后通知时间
        var newOrderList = orderList.stream()
                .peek(s -> s.setLastNotifyTime(new Date()))
                .collect(Collectors.toList());
        orderService.updateBatchById(newOrderList);
    }

    private Date getTimeBeforeNow(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR, calendar.get(Calendar.HOUR) - hour);
        return calendar.getTime();
    }

    private String buildNotifyMessage(List<OrderEntity> orderDTOList) {
        String title = "你有" + orderDTOList.size() + "个未处理订单，请及时处理！";
        Map<String, Object> model = new HashMap<>();
        model.put("title", title);
        model.put("orders", orderDTOList);

        try {
            var template = freeMarkerConfig.getConfiguration().getTemplate(templateName);
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (IOException | TemplateException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
