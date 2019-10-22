package com.resto.daily.web.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.MessageListener;
import com.resto.brand.core.util.DateUtil;
import com.resto.brand.core.util.MQSetting;
import com.resto.brand.core.util.MemcachedUtils;
import com.resto.brand.web.model.Brand;
import com.resto.brand.web.model.BrandSetting;
import com.resto.brand.web.model.ShopDetail;
import com.resto.brand.web.model.ShopMode;
import com.resto.brand.web.service.BrandService;
import com.resto.brand.web.service.BrandSettingService;
import com.resto.brand.web.service.ShopDetailService;
import com.resto.daily.web.rpcinterceptors.DataSourceTarget;
import com.resto.daily.web.util.RedisUtil;
import com.resto.daily.web.websocket.SystemWebSocketHandler;
import com.resto.shop.web.constant.Common;
import com.resto.shop.web.constant.PosVersion;
import com.resto.shop.web.constant.ProductionStatus;
import com.resto.shop.web.model.Order;
import com.resto.shop.web.model.OrderItem;
import com.resto.shop.web.posDto.ShopMsgChangeDto;
import com.resto.shop.web.service.OrderItemService;
import com.resto.shop.web.service.OrderService;
import com.resto.shop.web.service.PosService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.resto.brand.core.util.HttpClient.doPostAnsc;


@Component
public class OrderMessageListener implements MessageListener {
    Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    OrderService orderService;

    @Resource
    ShopDetailService shopDetailService;

    @Resource
    BrandService brandService;

    @Resource
    BrandSettingService brandSettingService;

    @Resource
    PosService posService;

    @Resource
    private OrderItemService orderItemService;

    private static String url = "http://10.25.23.60/pos/posAction";

    @Override
    public Action consume(Message message, ConsumeContext context) {
        Logger log = LoggerFactory.getLogger(getClass());
        log.info("接收到队列消息:" + message.getTag() + "@" + message);
        try {
            return executeMessage(message);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            log.error("字符编码转换错误:" + e.getMessage());
        }
        return Action.ReconsumeLater;
    }

    public Action executeMessage(Message message) throws UnsupportedEncodingException {
        String tag = message.getTag();
        log.info( "\n\n\n\n" + tag + "\n\n\n\n" );
        if (tag.equals(MQSetting.TAG_PLACE_ORDER)) {//下单打印叫号
            return executeSendMessage(message);
        } else if (tag.equals(MQSetting.TAG_PLACE_NOPAY_ORDER)) {
            return executeSendNoPayMessage(message);
        } else if (tag.equals(MQSetting.TAG_PLACE_PLATFORM_ORDER)) {
            return executeSendPlatformMessage(message);
        } else if (tag.equals(MQSetting.TAG_CHECK_ORDER)) {
            return executeCheckMessage(message);
        } else if (tag.equals(MQSetting.TAG_NOTICE_ORDER) || tag.equals(MQSetting.TAG_NOT_PRINT_ORDER)) {//拒绝订单通知pos端删数据
            return excuteNoticeOrderMessage(message);
        } else if (tag.equals(MQSetting.TAG_DELETE_ORDER)) {//模式5(后付款模式)订单支付后，移除pos端的订单列表
            return excuteOrderPaySuccessMessage(message);
        } else if (tag.equals(MQSetting.TAG_PRINT_SUCCESS)) {
            return excutePrintSuccessMessage(message);
        } else if (tag.equals(MQSetting.TAG_QUEUE_ORDER)) {//模式5(后付款模式)订单支付后，移除pos端的订单列表
            return excuteQueueOrderMessage(message);
        } else if (tag.equals(MQSetting.TAG_SHOP_CHANGE)) {
            return excuteShopChangeMessage(message);
        } else if (tag.equals(MQSetting.TAG_ORDER_CREATED)) {
            return excuteOrderCreatedMessage(message);
        } else if (tag.equals(MQSetting.TAG_ORDER_PAY)) {
            return excuteOrderPayMessage(message);
        } else if (tag.equals(MQSetting.TAG_ORDER_CANCEL)) {
            return excuteOrderCancelMessage(message);
        } else if (tag.equals(MQSetting.TAG_BAD_APPRAISE_PRINT_ORDER)) {//监听到差评打印订单的队列
            return excuteBadAppraisePrintOrder(message);
        } else if (tag.equals(MQSetting.TAG_RECEIPT_PRINT_SUCCESS)) {//监听到发票申请队列
            return excuteReceiptPrintOrder(message);
        } else if (tag.equals(MQSetting.TAG_SERVER_COMMAND)) {//监听到服务器发送的命令
            return excuteServerCommand(message);
        } else {
            return Action.ReconsumeLater;
        }
    }

    private Action excuteQueueOrderMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            JSONObject obj = new JSONObject(msg);
            String id = obj.optString("id");
            String shopId = obj.optString("shopId");
            SystemWebSocketHandler.queueOrderMessage(id, shopId);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    private Action excuteShopChangeMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            JSONObject obj = new JSONObject(msg);
            String shopMsgChangeDto = obj.optString("shopMsgChangeDto");
            ShopMsgChangeDto shopMsgChangeDtos = JSON.parseObject(shopMsgChangeDto, ShopMsgChangeDto.class);
            JSONObject data = new JSONObject();
            data.put("data", shopMsgChangeDto);
            data.put("dataType", "change");
            SystemWebSocketHandler.sendMsg(shopMsgChangeDtos.getShopId(), data.toString());
            ShopDetail shopDetail = shopDetailService.selectByPrimaryKey(shopMsgChangeDtos.getShopId());
            Brand brand = brandService.selectByPrimaryKey(shopMsgChangeDtos.getBrandId());
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shopDetail.getName());
            map.put("type", "newposAction");
            map.put("content", "发送到店铺:"+shopDetail.getName()+"，更改了后台shop的配置信息(同步shop配置信息),请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
            doPostAnsc(url, map);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    private Action excuteOrderCreatedMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            log.info("\n Pos2.0 发送新订单：" + order.getId() + "\n");
            String result = posService.syncOrderCreated(order.getId());
            log.info("\n Pos2.0 发送新订单：" + result + "\n");
            SystemWebSocketHandler.sendMsg(order.getShopDetailId(), result);
            Brand brand = brandService.selectByPrimaryKey(order.getBrandId());
            ShopDetail shopDetail = shopDetailService.selectByPrimaryKey(order.getShopDetailId());
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shopDetail.getName());
            map.put("type", "newposAction");
            map.put("content", "发送到店铺:"+shopDetail.getName()+"在pos创建订单，订单Id为:"+order.getId()+",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+result);
            doPostAnsc(url, map);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    private Action excuteOrderPayMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            log.error("订单：" + order.getId() + "支付成功向NewPos推送消息，消息内容为：" + msg);
            String result = posService.syncOrderPay(order.getId());
            log.info("\n Pos2.0 支付订单：" + result + "\n");
            SystemWebSocketHandler.sendMsg(order.getShopDetailId(), result);
            Brand brand = brandService.selectByPrimaryKey(order.getBrandId());
            ShopDetail shopDetail = shopDetailService.selectByPrimaryKey(order.getShopDetailId());
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shopDetail.getName());
            map.put("type", "newposAction");
            map.put("content", "发送到店铺:"+shopDetail.getName()+"订单支付，支付订单的id:"+order.getId()+",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+result);
            doPostAnsc(url, map);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    private Action excuteOrderCancelMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            JSONObject data = new JSONObject();
            data.put("dataType", "cancelOrder");
            data.put("orderId", order.getId());
            log.info("\n Pos2.0 取消订单：" + order.getId() + "\n");
            SystemWebSocketHandler.sendMsg(order.getShopDetailId(), data.toString());
            Brand brand = brandService.selectByPrimaryKey(order.getBrandId());
            ShopDetail shopDetail = shopDetailService.selectByPrimaryKey(order.getShopDetailId());
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shopDetail.getName());
            map.put("type", "newposAction");
            map.put("content", "发送到店铺:" + shopDetail.getName() + "取消订单，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
            doPostAnsc(url, map);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }


    private Action excuteNoticeOrderMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            order = orderService.selectById(order.getId());
            BrandSetting brandSetting = brandSettingService.selectByBrandId(order.getBrandId());
            ShopDetail shop = shopDetailService.selectById(order.getShopDetailId());
            if (order.getProductionStatus().equals(ProductionStatus.HAS_ORDER)) {
                SystemWebSocketHandler.cancelOrder(order, shop, brandSetting);
            }
            //删除准备中的该订单(yz 08/23)
            SystemWebSocketHandler.cancelRedayOrder(order, shop, brandSetting);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }

        return Action.CommitMessage;
    }

    private Action excutePrintSuccessMessage(Message message) throws UnsupportedEncodingException {
        String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
        com.alibaba.fastjson.JSONObject obj = com.alibaba.fastjson.JSONObject.parseObject(msg);
        JSONObject json = new JSONObject();
        String shopId = obj.getString("shopId");
        Integer count = (Integer) RedisUtil.get(shopId + "shopOrderCount");
        BigDecimal total = (BigDecimal) RedisUtil.get(shopId + "shopOrderTotal");
        json.put("orderCount", count);
        json.put("orderTotal", total);
        json.put("dataType", "money");
        try {
            SystemWebSocketHandler.sendMsg(shopId, json);
        } catch (InterruptedException e) {
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    private Action excuteOrderPaySuccessMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            Order mode = orderService.getOrderInfo(order.getId());
            order.setOrderMode(mode.getOrderMode());
            SystemWebSocketHandler.paySuccessMessage(order);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    private Action executeSendMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            Order mode = orderService.getOrderInfo(order.getId());
            ShopDetail shop = shopDetailService.selectById(order.getShopDetailId());
            Brand brand = brandService.selectById(order.getBrandId());
            BrandSetting brandSetting = brandSettingService.selectByBrandId(order.getBrandId());
            JSONArray jsonArray = new JSONArray();
            if(shop.getShopMode() == ShopMode.CALL_NUMBER && brandSetting.getCallTvType() == 2){
                if(order.getProductionStatus() == ProductionStatus.HAS_CALL){
                    Map<String, String> param = new HashMap<>();
                    param.put("orderId", order.getId());
                    List<OrderItem> orderItems = orderItemService.listByOrderId(param);
                    if(orderItems != null){
                        for(OrderItem item : orderItems){
                            JSONObject object = new JSONObject();
                            object.put("articleName", item.getArticleName());
                            object.put("count", item.getCount());
                            jsonArray.add(object);
                        }
                    }
                }
            }
            order.setOrderMode(mode.getOrderMode());
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shop.getName());
            map.put("type", "posAction");
            map.put("content", "订单" + order.getId() + "接收到消息队列TAG_PLACE_ORDER,请求服务器地址为:" + MQSetting.getLocalIP());
            doPostAnsc(url, map);
//        UserActionUtils.writeToFtp(LogType.ORDER_LOG, brand.getBrandName(), shop.getName(), mode.getId(),
//                "订单发送打印指令");
            String time = DateUtil.formatDate(new Date(), "yyyy-MM-dd hh:mm:ss");
            if (shop.getShopMode() == ShopMode.CALL_NUMBER && brandSetting.getCallTvType() == 1) {
                SystemWebSocketHandler.receiveOrderUmeng(order, shop, brandSetting);//发送到Pos和Tv端 的webSocket
            } else {
                log.info("发送到pos或者tv端的webSocket" + order.getId() + "orderState：" + order.getOrderState() + "production：" + order.getProductionStatus() + "时间：" + time);
                SystemWebSocketHandler.getInstance().receiveOrder(order, jsonArray);//发送到Pos和Tv端 的webSocket
            }
            if(shop.getPosVersion() == PosVersion.VERSION_2_0
                    && MemcachedUtils.add(order.getId() + "actionPrint", 1, 600)
                    && shop.getShopMode() == ShopMode.CALL_NUMBER){
//                RedisUtil.set(order.getId() + "actionPrint", 600);
                JSONObject jo = new JSONObject();
                jo.put("dataType", "actionPrint");
                jo.put("orderId", order.getId());
                log.info("\n Pos2.0 发送打印指令：" + order.getId() + "\n");
                SystemWebSocketHandler.sendMsg(order.getShopDetailId(), jo.toString());
                Map maporder = new HashMap(4);
                maporder.put("brandName", brand.getBrandName());
                maporder.put("fileName", shop.getName());
                maporder.put("type", "newposAction");
                maporder.put("content", "发送到店铺:" + shop.getName() + "打印指令，订单Id为:" + order.getId() + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+jo.toString());
                doPostAnsc(url, maporder);
            }
//        SocketThread.receiveOrder(order);//发送到安卓Tv端 的 Socket
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }

        return Action.CommitMessage;
    }

    private Action executeSendNoPayMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            Order mode = orderService.getOrderInfo(order.getId());
            ShopDetail shop = shopDetailService.selectById(order.getShopDetailId());
            Brand brand = brandService.selectById(order.getBrandId());
            order.setOrderMode(mode.getOrderMode());
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shop.getName());
            map.put("type", "posAction");
            map.put("content", "订单" + order.getId() + "接收到消息队列TAG_PLACE_NOPAY_ORDER,请求服务器地址为:" + MQSetting.getLocalIP());
            doPostAnsc(url, map);
            SystemWebSocketHandler.receiveNoPayOrder(order, shop);//发送到Pos和Tv端 的webSocket
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }

        return Action.CommitMessage;
    }

    private Action executeSendPlatformMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            JSONObject obj = new JSONObject(msg);
            String brandId = obj.optString("brandId");
            String id = obj.optString("id");
            Integer type = obj.optInt("type");
            String shopId = obj.optString("shopId");
            DataSourceTarget.setDataSourceName(brandId);
            ShopDetail shop = shopDetailService.selectById(shopId);
            Brand brand = brandService.selectById(brandId);
            Map map = new HashMap(4);
            map.put("brandName", brand.getBrandName());
            map.put("fileName", shop.getName());
            map.put("type", "posAction");
            map.put("content", "外卖订单" + id + "接收到消息队列TAG_PLACE_PLATFORM_ORDER,请求服务器地址为:" + MQSetting.getLocalIP());
            doPostAnsc(url, map);
            if(shop.getPosVersion() == PosVersion.VERSION_2_0){
                String result = posService.syncPlatform(id);
                if(result != null){
                    log.info("\n Pos2.0 外卖订单：" + id + "\n");
                    if(MemcachedUtils.add(id + "platformActionPrint", 1, 600)){
                        SystemWebSocketHandler.sendMsg(shopId, result);
                    }
                    Map mapPlatOrder = new HashMap(4);
                    mapPlatOrder.put("brandName", brand.getBrandName());
                    mapPlatOrder.put("fileName", shop.getName());
                    mapPlatOrder.put("type", "newposAction");
                    mapPlatOrder.put("content", "发送到店铺:"+shop.getName()+"外卖订单,订单id:"+id+",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+result);
                    doPostAnsc(url, mapPlatOrder);
                }
            }
            SystemWebSocketHandler.sendPlatformOrderToPos(shopId, id, type);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }


    private Action executeCheckMessage(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            Order order = JSON.parseObject(msg, Order.class);
            DataSourceTarget.setDataSourceName(order.getBrandId());
            Boolean timeOut = order.getTimeOut();
            log.info("\n\n【收到订单验证消息】orderId：" + order.getId() + "\n\n");
            order = orderService.selectById(order.getId());
            //如果生产状态还没有打印，那么发送消息队列
            if (order.getProductionStatus() < ProductionStatus.PRINTED) {
                Order mode = orderService.getOrderInfo(order.getId());
                order.setOrderMode(mode.getOrderMode());
                SystemWebSocketHandler.receiveOrder(order, null);
                log.info("\n\n【执行订单发送到POS端命令】orderId：" + order.getId() + "\n\n");
            }
            //如果循环结束 还没有打印，那么设置异常
            if (timeOut && order.getProductionStatus() < ProductionStatus.PRINTED) {
                orderService.setOrderPrintFail(order.getId());
                log.info("\n\n【订单被设为异常订单】orderId：" + order.getId() + "\n\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }

        return Action.CommitMessage;
    }

    /**
     * 接收到差评打单的消息队列向webSocket推送信息
     * @param message
     * @return
     * @throws UnsupportedEncodingException
     */
    private Action excuteBadAppraisePrintOrder(Message message) throws UnsupportedEncodingException {
        try {
            String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
            JSONObject obj = new JSONObject(msg);
            String orderId = obj.optString("orderId");
            String shopId = obj.optString("shopId");
            log.info("监听到差评的订单：" + orderId + "--所在店铺：" + shopId);
            SystemWebSocketHandler.sendBadAppraisePrintOrderToPos(orderId,shopId);
        }catch (Exception e){
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    /**
     *发票管理推送消息
     */
    private Action excuteReceiptPrintOrder(Message message) throws UnsupportedEncodingException {
        String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
        com.alibaba.fastjson.JSONObject obj = com.alibaba.fastjson.JSONObject.parseObject(msg);
        JSONObject json = new JSONObject();
        String shopId = obj.getString("shopId");
        String orderNumber = obj.getString("orderNumber");
        json.put("orderNumber",orderNumber);
        json.put("dataType","receipt");
        log.info("接收到发票订单号:" + orderNumber);
        try {
            SystemWebSocketHandler.sendReceiptPrintOrder(shopId,json);
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

    /**
     * 将服务器发送的命令推送至 POS 端
     */
    private Action excuteServerCommand(Message message) throws UnsupportedEncodingException {
        String msg = new String(message.getBody(), MQSetting.DEFAULT_CHAT_SET);
        com.alibaba.fastjson.JSONObject obj = com.alibaba.fastjson.JSONObject.parseObject(msg);
        JSONObject json = new JSONObject();
        json.put("dataType",obj.getString("dataType"));
        json.put("data",obj.getString("data"));
        String shopId = obj.getString("shopId");
        log.info("\n\n接收到服务器的命令：" + msg);
        try {
            SystemWebSocketHandler.sendMsg(shopId, json.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return Action.ReconsumeLater;
        }
        return Action.CommitMessage;
    }

}
