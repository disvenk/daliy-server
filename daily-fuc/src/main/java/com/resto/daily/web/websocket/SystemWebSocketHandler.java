package com.resto.daily.web.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.resto.brand.core.util.ApplicationUtils;
import com.resto.brand.core.util.DateUtil;
import com.resto.brand.core.util.MQSetting;
import com.resto.brand.core.util.StringUtils;
import com.resto.brand.web.model.Brand;
import com.resto.brand.web.model.BrandSetting;
import com.resto.brand.web.model.ShopDetail;
import com.resto.brand.web.model.ShopMode;
import com.resto.brand.web.service.BrandService;
import com.resto.brand.web.service.ShopDetailService;
import com.resto.daily.web.android.AndroidNotification;
import com.resto.daily.web.android.AndroidUnicast;
import com.resto.daily.web.android.PushClient;
import com.resto.daily.web.rpcinterceptors.DataSourceTarget;
import com.resto.daily.web.util.RedisUtil;
import com.resto.shop.web.constant.*;
import com.resto.shop.web.model.Announce;
import com.resto.shop.web.model.Kitchen;
import com.resto.shop.web.model.OffLineOrder;
import com.resto.shop.web.model.Order;
import com.resto.shop.web.service.CloseShopService;
import com.resto.shop.web.service.KitchenService;
import com.resto.shop.web.service.PosService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.resto.brand.core.util.HttpClient.doPostAnsc;

@Component
public class SystemWebSocketHandler implements WebSocketHandler {

    @Autowired
    private PosService posService;


    @Autowired
    CloseShopService closeShopService;

    @Autowired
    ShopDetailService shopDetailService;

    @Autowired
    BrandService brandService;

    @Autowired
    KitchenService kitchenService;

    private static final Logger log = LoggerFactory.getLogger(SystemWebSocketHandler.class);

    /**
     * 保存 pos 端  WebSocketSession
     */
    private static final Map<String, Map<String, WebSocketSession>> PosClients = new ConcurrentHashMap<>();

    /**
     * 保存 pos 端  WebSocketSession pos2.0
     */
    private static final Map<String, Map<String, WebSocketSession>> PosClients_2_0 = new ConcurrentHashMap<>();


    /**
     * 保存 tv 端  WebSocketSession
     */
    private static final Map<String, Map<String, WebSocketSession>> TvClients = new ConcurrentHashMap<>();

    /**
     * 保存 geekpos 端  WebSocketSession
     */
    private static final Map<String, Map<String, WebSocketSession>> GeekPosClients = new ConcurrentHashMap<>();

    /**
     * 保存 相应 店铺   pos 端  未显示的订单
     */
    private static final Map<String, List<Order>> PosHistoryOrder = new ConcurrentHashMap<>();

    /**
     * 保存 相应 店铺   pos 端  未显示的订单
     */
    private static final Map<String, List<Announce>> PosHistoryAnnounce = new ConcurrentHashMap<>();

    /**
     * 保存 相应 店铺   tv 端  未显示的订单
     */
    private static final Map<String, List<Order>> TvHistoryOrder = new ConcurrentHashMap<>();


    /**
     * 需要打印的订单队列
     */

    private static final List<String> orderList = new ArrayList<>();

    private static PushClient client = new PushClient();


    private static String url = "http://139.196.222.182:8580/pos/posAction";


    //使用volatile关键字保其可见性
    volatile private static SystemWebSocketHandler systemWebSocketHandler = null;

    private SystemWebSocketHandler() {
    }

    public static SystemWebSocketHandler getInstance() {
        try {
            if (systemWebSocketHandler == null) {//懒汉式
                //创建实例之前可能会有一些准备性的耗时工作
                Thread.sleep(300);
                synchronized (SystemWebSocketHandler.class) {
                    if (systemWebSocketHandler == null) {//二次检查
                        systemWebSocketHandler = new SystemWebSocketHandler();
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return systemWebSocketHandler;
    }


    /**
     * 用于推送消息
     *
     * @param order
     */
    public static void receiveOrder(Order order, JSONArray orderItems) {
        log.info("\n【订单生产状态】   " + order.getProductionStatus());
        log.info("\n【订单店铺模式】   " + order.getOrderMode());
        JSONObject data = new JSONObject(order);
        try {
            log.error("\n【订单的店铺模式：】" +order.getOrderMode() + "\n【订单的打印次数：】" + order.getPrintTimes());
            if (order.getOrderMode() == ShopMode.BOSS_ORDER && order.getPrintTimes() == 1) {
                log.error("\n【发送指令了：】");
                sendOrderToPos(order.getShopDetailId(), order);
            }
            if (order.getPrintOrderTime() != null && order.getOrderMode() != ShopMode.CALL_NUMBER) {
                log.info("\n该订单已打印：" + order.getId());
                return;
            }
            if (order.getPrintTimes() != 1) {
                if (order.getProductionStatus().equals(ProductionStatus.PRINTED)
                        && !order.getOrderMode().equals(ShopMode.CALL_NUMBER)) {
                    //如果订单状态已经打印，那么从打印队列中去除此订单
                    orderList.remove(order.getId());
                    log.info("\n订单状态已经打印，从预推送订单列表中移除：" + order.getId());
                    return;
                }
                if ((order.getProductionStatus().equals(ProductionStatus.PRINTED)
                        || order.getProductionStatus().equals(ProductionStatus.HAS_CALL))
                        && order.getOrderMode().equals(ShopMode.CALL_NUMBER)) {
                    orderList.remove(order.getId());
                    log.info("\n【电视叫号】订单：" + order.getId());
                }

                //如果订单在打印队列中
                if (orderList.contains(order.getId())) {
                    log.info("\n打印队列包含此订单：" + order.getId());
                    return;
                }

                orderList.add(order.getId());
            }

            log.info("\n【接收到新订单】" + order.getId() + " \n【productionStatus】:" + order.getProductionStatus());

            int producStatus = order.getProductionStatus();
            if (ProductionStatus.HAS_ORDER == producStatus && order.getOrderMode() == ShopMode.MANUAL_ORDER){
                sendOrderToPos(order.getShopDetailId(), order);
            }
            if ((ProductionStatus.PRINTED > producStatus) || (producStatus == ProductionStatus.PRINTED && order.getOrderMode().equals(ShopMode.HOUFU_ORDER))) {//	Pos   --->   新订单
                log.info("推送到pos端");
                sendOrderToPos(order.getShopDetailId(), order);
            } else if (ProductionStatus.PRINTED == producStatus) {//  TV --->  准备中  或者  叫号
                data.put("operationType", "ready");
                sendOrderToTvTest(order.getShopDetailId(), data);
            } else if (ProductionStatus.HAS_CALL == producStatus) {
                data.put("operationType", "call");
                data.put("callData", orderItems);
                sendOrderToTvTest(order.getShopDetailId(), data);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void receiveOrderUmeng(Order order, final ShopDetail shop, BrandSetting brandSetting) {
        log.info("\n【订单生产状态】   " + order.getProductionStatus());
        log.info("\n【订单店铺模式】   " + order.getOrderMode());
        try {
            if (order.getOrderMode() == ShopMode.BOSS_ORDER && order.getPrintTimes() == 1) {
                sendOrderToPos(order.getShopDetailId(), order);
            }
            if (order.getPrintOrderTime() != null && order.getOrderMode() != ShopMode.CALL_NUMBER) {
                log.info("\n该订单已打印：" + order.getId());
                return;
            }
            if (order.getPrintTimes() != 1) {
                if (order.getProductionStatus().equals(ProductionStatus.PRINTED)
                        && !order.getOrderMode().equals(ShopMode.CALL_NUMBER)) {
                    //如果订单状态已经打印，那么从打印队列中去除此订单
                    orderList.remove(order.getId());
                    log.info("\n订单状态已经打印，从预推送订单列表中移除：" + order.getId());
                    return;
                }

                if ((order.getProductionStatus().equals(ProductionStatus.PRINTED)
                        || order.getProductionStatus().equals(ProductionStatus.HAS_CALL))
                        && order.getOrderMode().equals(ShopMode.CALL_NUMBER)) {
                    orderList.remove(order.getId());
                    log.info("\n【电视叫号】订单：" + order.getId());
                }

                //如果订单在打印队列中
                if (orderList.contains(order.getId())) {
                    log.info("\n打印队列包含此订单：" + order.getId());
                    return;
                }

                orderList.add(order.getId());
            }


            log.info("\n【接收到新订单】" + order.getId() + " \n【productionStatus】:" + order.getProductionStatus());

            int producStatus = order.getProductionStatus();
            if ((ProductionStatus.PRINTED > producStatus) || (producStatus == ProductionStatus.PRINTED && order.getOrderMode().equals(ShopMode.HOUFU_ORDER))) {//	Pos   --->   新订单
                log.info("推送到pos端");
                sendOrderToPos(order.getShopDetailId(), order);
            } else if (ProductionStatus.PRINTED == producStatus) {//  TV --->  准备中  或者  叫号
                log.info("推送到tv端" + order.getProductionStatus());
                if (brandSetting.getOpenAndriodApk() == 1) {
                    try {
                        sendAndroidUnicast("new", order.getVerCode(), order.getId(), "", shop);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    sendOrderToTv(order.getShopDetailId(), order);
                }
            } else if (ProductionStatus.HAS_CALL == producStatus) {
                if (brandSetting.getOpenAndriodApk() == 1) {
                    try {
                        sendAndroidUnicast("call", order.getVerCode(), order.getId(), "", shop);
                        final String verCode = order.getVerCode();
                        final String orderId = order.getId();
                        Timer timer = new Timer();//实例化Timer类
                        timer.schedule(new TimerTask() {
                            public void run() {
                                try {
                                    sendAndroidUnicast("remove", verCode, orderId, "", shop);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                this.cancel();
                            }
                        }, 300000);//延迟五分钟
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    sendOrderToTv(order.getShopDetailId(), order);
                }
            }
        } catch (InterruptedException e) {
            log.error("\n99【线程休眠报错】");
            e.printStackTrace();
        } catch (Exception e) {
            log.error("\n102【推送新订单时报错了】");
            e.printStackTrace();
        }
    }


    public static void receiveNoPayOrder(Order order, final ShopDetail shop) {
        try {
            if (order.getOrderState() == OrderState.SUBMIT && order.getProductionStatus() == ProductionStatus.NOT_ORDER && (order.getPayMode() == OrderPayMode.YL_PAY || order.getPayMode() == OrderPayMode.XJ_PAY || order.getPayMode() == OrderPayMode.SHH_PAY
                    || order.getPayMode() == OrderPayMode.JF_PAY)) {
                sendOrderToPosNoPay(order.getShopDetailId(), order);
            } else if (order.getOrderState() == OrderState.PAYMENT && order.getProductionStatus() == ProductionStatus.HAS_ORDER && (order.getPayMode() == OrderPayMode.YL_PAY || order.getPayMode() == OrderPayMode.XJ_PAY || order.getPayMode() == OrderPayMode.SHH_PAY
                    || order.getPayMode() == OrderPayMode.JF_PAY)) {
                sendOrderToPosNoPay(order.getShopDetailId(), order);
            } else if ((order.getOrderState() == OrderState.SUBMIT || order.getOrderState() == OrderState.PAYMENT || order.getOrderState() == OrderState.CONFIRM) && order.getProductionStatus() == ProductionStatus.PRINTED
                    && order.getPayType() == PayType.NOPAY && order.getOrderMode() == ShopMode.BOSS_ORDER) {
                sendOrderToPosNoPay(order.getShopDetailId(), order);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 移除订单
     *
     * @param order
     */
    public static void cancelOrder(Order order, ShopDetail shop, BrandSetting brandSetting) {
        //移除 历史集合中的数据
        removeHistoryOrder(order, PosHistoryOrder);
        removeHistoryOrder(order, TvHistoryOrder);
        //通知 前台，移除相应的订单信息
        JSONObject data = new JSONObject(order);
        data.put("dataType", "cancelOrder");
        TextMessage returnMessage = new TextMessage(data.toString());
        try {
            log.info("开始链接tv端移除订单----------");
            if (shop.getShopMode() == ShopMode.CALL_NUMBER && brandSetting.getCallTvType() == 1) {
                try {
                    sendAndroidUnicast("remove", order.getVerCode(), order.getId(), "", shop);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                data.put("operationType", "cancelOrder");
                log.info("推送了拒绝或者取消订单，在TV准备中消失！");
                sendOrderToTvTest(order.getShopDetailId(), data);
            }
        } catch (Exception e) {
            log.error("125----> 移除 Tv 端 订单信息失败");
            e.printStackTrace();
        }
    }

    /**
     * 模式5(后付款模式)订单支付后，移除pos端的订单列表
     *
     * @param order
     */
    public static void paySuccessMessage(Order order) {
        //移除 历史集合中的数据
        removeHistoryOrder(order, PosHistoryOrder);

        //通知 前台，移除相应的订单信息
        JSONObject data = new JSONObject();
        List<Order> orders = new ArrayList<>();
        orders.add(order);
        data.put("orders", orders);
        data.put("dataType", "paySuccess");
        TextMessage returnMessage = new TextMessage(data.toString());
        try {
            log.info("开始链接Pos端移除订单----------");
            sendMsg(PosClients, order.getShopDetailId(), returnMessage);
        } catch (Exception e) {
            log.error("150----> 移除 Pos 端 订单信息失败");
            e.printStackTrace();
        }
    }

    /**
     * 移除指定 历史集合保存 的指定的订单信息
     *
     * @param order
     * @param historyOrder
     */
    public static void removeHistoryOrder(Order order, Map<String, List<Order>> historyOrder) {
        String shopId = order.getShopDetailId();
        //移除历史订单
        List<Order> orderList = historyOrder.get(shopId);
        if (orderList != null && !orderList.isEmpty()) {
            Iterator<Order> it = orderList.iterator();
            while (it.hasNext()) {
                if (it.next().equals(order.getId())) {
                    it.remove();
                    historyOrder.put(shopId, orderList);
                    break;
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus arg1) throws Exception {
        log.info("\n\n\n   【afterConnectionClosed】\n\n\n");
        if (session.isOpen()) {
            session.close();
        }
        if(RedisUtil.get(session.getId()) != null){
            String shopId = RedisUtil.get(session.getId()).toString();
            log.info("\n\n  【afterConnectionClosed】  断开连接：     sessionID : " + session.getId() + "          ShopID：" + shopId);
            log.info("移除前：" + getClientsByShopId(PosClients_2_0, shopId).toString());
            log.info("\n\n");
            removeSession(PosClients_2_0, shopId, session);
            log.info("移除后：" + getClientsByShopId(PosClients_2_0, shopId).toString());
            RedisUtil.remove(session.getId());
            Map<String, WebSocketSession> sessionMap = PosClients_2_0.get(shopId);
            if(sessionMap.isEmpty() || sessionMap.size() == 0){
                RedisUtil.set(shopId + "loginStatus", false);
                log.info("\n\n  shopID：" + shopId + "  Pos2.0 没有任何在线客户端\n\n");
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("\n\n\n   【afterConnectionEstablished】\n\n\n");
    }

    /**
     * 客户端发送消息时 触发
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        JSONObject data = new JSONObject(message.getPayload().toString());
        log.info("客户端发送的消息为：  " + data);
        String type = data.getString("type");
        String id, shopId, brandId;
        ShopDetail shopDetail = null;
        Brand brand = null;

        brandId = data.optString("brandId", null);
        shopId = data.getString("shopId");

        if("heartbeat".equals(type)){   // 心跳，如果存在 shopId 则设置店铺为在线状态
            //心跳则直接退出方法，不做操作
            RedisUtil.set(shopId + "loginStatus", true, 40L);
            return;
        }
        //查询出对应的品牌和店铺
        if (StringUtils.isNotBlank(brandId) && StringUtils.isNotBlank(shopId)) {
            shopDetail = shopDetailService.selectById(shopId);
            brand = brandService.selectByPrimaryKey(brandId);
        }
        //定义返回的回调信息
        String returnInfo;
        Map map = new HashMap(4);
        map.put("brandName", brand.getBrandName());
        map.put("fileName", shopDetail.getName());
        map.put("type", "newposAction");
        switch (type) {
            case "register":  //    待删除 ... 2017年11月24日 11:46:53
                saveClientsSession("pos2.0", shopId, session);
                RedisUtil.set(session.getId(), shopId);
                RedisUtil.set(shopId + "loginStatus", true);
                // todo 重写 发送历史消息方法
                sendLocalPosRequestCallBackMsg(data, null);
                break;
            case "login":    //Pos 2.0 登录方法，用于向服务器注册，保存身份
                String client = "pos2.0";
                // todo 重写 发送历史消息方法
                JSONObject result = new JSONObject();
                result.put("success", true);

                //-------wyj   newpos登录保存信息
                saveClientsSession(client, shopId, session);
                RedisUtil.set(session.getId(), shopId);
                RedisUtil.set(shopId + "loginStatus", true, 40L);

                if(shopDetail == null){
                    result.put("success", false);
                    result.put("type", 1);  //  禁止登录    1 - 10 ：登录错误
                    result.put("message", "shopId错误");
                }else if(shopDetail.getIsOpen() == false){
                    result.put("success", false);
                    result.put("type", 2);  //  禁止登录    1 - 10 ：登录错误
                    result.put("message", "【" + shopDetail.getName() +"】已关闭，请联系管理员开启店铺");
                }else if(shopDetail.getPosVersion() == PosVersion.VERSION_1_0){
                    result.put("success", false);
                    result.put("type", 3);  //  禁止登录    1 - 10 ：登录错误
                    result.put("message", "【" + shopDetail.getName() +"】暂未开启 Pos 2.0 版本！");
                }
                sendLocalPosRequestCallBackMsg(data, result.toString());
                map.put("content", "接收到店铺:"+shopDetail.getName()+"登录了newpos,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "logout":    //Pos 登出接口
                removeSession(PosClients_2_0, shopId, session);
                RedisUtil.remove(session.getId());
                if(PosClients_2_0.containsKey(shopId)){
                    Map<String, WebSocketSession> sessionMap = PosClients_2_0.get(shopId);
                    if(sessionMap.isEmpty() || sessionMap.size() == 0){
                        RedisUtil.set(shopId + "loginStatus", false);
                    }
                }
                map.put("content", "接收到店铺:"+shopDetail.getName()+"退出了newpos,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "articleStock":    //  Pos端主动获取服务器菜品库存
                DataSourceTarget.setDataSourceName(brandId);
                sendLocalPosRequestCallBackMsg(data, posService.syncArticleStock(shopId));
                map.put("content", "接收到店铺:"+shopDetail.getName()+"主动拉取菜品库存,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "activated":
                //上架、下架
                id = data.getString("id");
                Integer activated = data.getInt("activated");
                DataSourceTarget.setDataSourceName(brandId);
                posService.articleActived(id, activated);
                if (activated.equals(0)) {
                    map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos端下架了菜品Id为:" + id + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                } else {
                    map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos端上架了菜品Id为:" + id + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                }
                break;
            case "empty":
                DataSourceTarget.setDataSourceName(brandId);
                id = data.getString("id");
                returnInfo= posService.articleEmpty(id);
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos端估清了菜品Id为:" + id + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "edit":
                DataSourceTarget.setDataSourceName(brandId);
                id = data.getString("id");
                Integer count = data.getInt("count");
                posService.articleEdit(id, count);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos端编辑了菜品Id为:" + id + "菜品库存为:"+count+",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "printSuccess":
                log.info("\n\n\n    【打印成功】 webSocket    orderId：" + data.getString("orderId") + "\n\n\n\n");
                DataSourceTarget.setDataSourceName(brandId);
                posService.printSuccess(data.getString("orderId"));
                sendLocalPosRequestCallBackMsg(data, null);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos打印订单成功，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "orderCreated":
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.syncPosCreateOrder(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos创建订单,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "orderPay":
                log.info("接收到NewPos买单的请求：" + data);
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.syncPosOrderPay(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos支付订单，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "refundOrder":
                DataSourceTarget.setDataSourceName(brandId);
                // TODO: 2017/10/13 回调至本地Pos 告知已更改成功。
//                sendMsg(data.getString("shopId"), posService.syncPosRefundOrder(data.toString()));
                sendLocalPosRequestCallBackMsg(data, posService.syncPosRefundOrder(data.toString()));
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos退菜,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "confirmOrder":
                DataSourceTarget.setDataSourceName(brandId);
                String orderId = data.getString("orderId");
                posService.syncPosConfirmOrder(orderId);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos确认收款，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "changeTable":
                DataSourceTarget.setDataSourceName(brandId);
                String oId = data.getString("orderId");
//                String tNumber = data.getString("tableNumber");
                String tNumber = data.get("tableNumber").toString();
                returnInfo = posService.syncChangeTable(oId,tNumber);
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos更换桌位订单Id为:" + data.getString("orderId") + "新桌号为:"+tNumber+",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "updateTableState":
                DataSourceTarget.setDataSourceName(brandId);
                String tableNumber = data.get("tableNumber").toString();
                Boolean state = data.getBoolean("state");
                returnInfo = posService.syncTableState(shopId, tableNumber, state, data.optString("orderId"));
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                if (state) {
                    map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos端释放桌号:" + tableNumber + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                } else {
                    map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos端锁定桌号:" + tableNumber + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                }
                break;
            case "syncPosLocalOrder":
                DataSourceTarget.setDataSourceName(brandId);
                if(posService.syncPosLocalOrder(data.toString())){
                    sendLocalPosRequestCallBackMsg(data, null);
                    map.put("content", "接收到店铺:" + shopDetail.getName() + "同步newpos本地订单到服务器,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                }
                break;
            case "posCancelOrder":
                orderId = data.getString("orderId");
                DataSourceTarget.setDataSourceName(brandId);
                posService.posCancelOrder(shopId, orderId);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos取消订单，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "callNumber":
                orderId = data.getString("orderId");
                DataSourceTarget.setDataSourceName(brandId);
                posService.posCallNumber(orderId);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos叫号订单，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "printOrder":
                orderId = data.getString("orderId");
                DataSourceTarget.setDataSourceName(brandId);
                posService.posPrintOrder(orderId);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "在pos更改订单状态变成2，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "exceptionOrderList":
                DataSourceTarget.setDataSourceName(brandId);
                log.info("\n\n\n    exceptionOrderList  lmx");
                log.info("\n\n\n");
                log.info(posService.serverExceptionOrderList(shopId));
                log.info("\n\n\n");
                sendLocalPosRequestCallBackMsg(data, posService.serverExceptionOrderList(shopId));
                map.put("content", "接收到店铺:" + shopDetail.getName() + "newpos拉取服务器异常订单列表,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "checkOut":
                DataSourceTarget.setDataSourceName(brandId);
                OffLineOrder offLineOrder = new OffLineOrder(ApplicationUtils.randomUUID(), shopId, brandId , 1, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0, new Date(), new Date(), 1);
                closeShopService.insertOffLineOrder(brandId, shopId, offLineOrder);
                posService.posCheckOut(brandId, shopId, offLineOrder);
                sendLocalPosRequestCallBackMsg(data, null);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "newpos在本地结店,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "scanCodePayment":
                //newPos扫码支付
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.scanCodePayment(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "扫码支付的请求,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "confirmPayment":
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.confirmPayment(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "查询扫码订单的请求,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "revocationOfOrder":
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.revocationOfOrder(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "撤销支付订单的请求,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "updateData":
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.updateData(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "动态修改数据的请求,请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "getAccountInfo":
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.useAccountAndCoupon(data.getString("orderId"));
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "获取账户信息的请求，订单Id为:" + data.getString("orderId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "synchronousData":
                DataSourceTarget.setDataSourceName(brandId);
                returnInfo = posService.synchronousData(data.toString());
                sendLocalPosRequestCallBackMsg(data, returnInfo);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "同步数据的请求，发送的数据为:" + data + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
            case "updateKitchenStatus":
                DataSourceTarget.setDataSourceName(brandId);
                String kitchenId = data.optString("kitchenId");
                String status = data.optString("status");
//                Kitchen kitchen = new Kitchen();
//                kitchen.setId(Integer.valueOf(kitchenId));
//                kitchen.setShopDetailId(shopId);
//                kitchen.setStatus(Integer.valueOf(status));
                kitchenService.updateKitchenStatus(Integer.valueOf(kitchenId),Integer.valueOf(status),shopId);
                map.put("content", "接收到店铺:" + shopDetail.getName() + "获取账户信息的请求，厨房Id为:" + data.getString("kitchenId") + ",请求服务器地址为:" + MQSetting.getLocalIP() + "\n-------请求内容:"+data.toString());
                break;
        }
        //发送日志
        doPostAnsc(url, map);
        //如果为 参数为 check 则将当前的 session 保存在 对应的Map中
        if (("check").equals(type)) {
            String client = data.getString("client");
            String key = data.getString("shopId");
            saveClientsSession(client, key, session);
            checkHistoryOrder(key, client, session);
        } else if (("heartbeat").equals(type)) {
            // 客户端 心跳
            RedisUtil.set(shopId + "loginStatus", true, 40L);
            System.out.println("【heartbeat】shopId：" + data.getString("shopId") + "     client：" + data.getString("client") + "     date：" + DateUtil.getTime());
        } else if ("printFalse".equals(type)) {
            String content = data.getString("content");
            Map map1 = new HashMap(4);
            map1.put("brandName", "printFalse");
            map1.put("fileName", "printFalse");
            map1.put("type", "printFail");
            map1.put("content", content);
            doPostAnsc(url, map1);
        } else {
            // 客户端发送信息了  ---->    处理信息
            log.info("处理信息 ----- sessionID：" + session.getId() + "  Msg：" + data);
        }
    }

    /**
     * 触发 本地Pos（pos2.0） 请求回调函数
     * @param requestData
     * @throws InterruptedException
     */
    public void sendLocalPosRequestCallBackMsg(JSONObject requestData, String callBackData) throws InterruptedException {
        if(requestData.has("requestId")){
            JSONObject resultData = new JSONObject();
            resultData.put("dataType","callback");
            resultData.put("requestId",requestData.getString("requestId"));
            resultData.put("callBackData",callBackData);
            sendMsg(requestData.getString("shopId"), resultData.toString());
        }
    }

    /**
     * 获取当前 客户端 链接信息
     * @param clientsMap
     * @param shopId
     */
    public void getClientConnInfo(Map<String, Map<String, WebSocketSession>> clientsMap, String shopId){
        Map<String, WebSocketSession> sessionMap = clientsMap.get(shopId);
    }

    /**
     * 给 本地Pos（pos2.0）发送历史消息
     * @param key
     */
    public void sendLocalPosHistoryAnnounce(String key) throws InterruptedException {
        List<Announce> history = PosHistoryAnnounce.get(key);
//        if (!CollectionUtils.isEmpty(history)) {
        if (history != null && history.size() > 0) {
            for (Announce announce : history) {
                sendMsg(key, announce.getContent());
            }
            PosHistoryAnnounce.remove(key);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable arg1) throws Exception {
        log.info("\n\n\n   【handleTransportError】\n\n\n");
        if (session.isOpen()) {
            session.close();
        }
        if(RedisUtil.get(session.getId()) != null){
            String shopId = RedisUtil.get(session.getId()).toString();
//            log.info("\n\n  【handleTransportError】 断开连接：     sessionID : " + session.getId() + "          ShopID：" + shopId);
//            log.info("断开前：" + getClientsByShopId(PosClients_2_0, shopId).toString());
//            log.info("\n\n");
            removeSession(PosClients_2_0, shopId, session);
//            log.info("断开后：" + getClientsByShopId(PosClients_2_0, shopId).toString());
            RedisUtil.remove(session.getId());
            Map<String, WebSocketSession> sessionMap = PosClients_2_0.get(shopId);
            if(sessionMap.isEmpty() || sessionMap.size() == 0){
                RedisUtil.set(shopId + "loginStatus", false);
//                log.info("\n\n  shopID：" + shopId + "  Pos2.0 没有任何在线客户端\n\n");
            }
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        log.info("\n\n\n   【supportsPartialMessages】\n\n\n");
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * 给指定的  Pos 端 发送 订单信息
     *
     * @param shopId
     * @param order
     * @throws InterruptedException
     * @throws IOException
     */
    public static void sendOrderToPos(String shopId, Order order) throws InterruptedException {
        JSONObject data = new JSONObject(order);
        data.put("dataType", "current");
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    try {
                        entry.getValue().sendMessage(returnMessage);
                        isOpen = true;
                    } catch (Exception e) {
//						暂未做停止处理
//						Thread.sleep(3000);
//						sendOrderToPos(shopId, order);
                        e.printStackTrace();
                    }
                } else {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    keys.add(entry.getKey());
                }
            }
            if (!isOpen) {
                savaHistoryOrder(order, PosHistoryOrder);
            }
            posMap = removeMapVal(posMap, keys);
            PosClients.put(shopId, posMap);
        } else {
            savaHistoryOrder(order, PosHistoryOrder);
        }
    }


    public static void sendMsg(String shopId, JSONObject data) throws InterruptedException {
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    try {
                        entry.getValue().sendMessage(returnMessage);
                        isOpen = true;
                    } catch (Exception e) {
//						暂未做停止处理
//						Thread.sleep(3000);
//						sendOrderToPos(shopId, order);
                        e.printStackTrace();
                    }
                } else {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    keys.add(entry.getKey());
                }
            }
            posMap = removeMapVal(posMap, keys);
            PosClients.put(shopId, posMap);
        }
    }


    public static void sendMsg(String shopId, String data) throws InterruptedException {
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients_2_0.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    try {
                        log.info("向NewPos发送回调信息：" + JSON.toJSONString(returnMessage));
                        entry.getValue().sendMessage(returnMessage);
                        isOpen = true;
                    } catch (Exception e) {
//						暂未做停止处理
//						Thread.sleep(3000);
//						sendOrderToPos(shopId, order);
                        e.printStackTrace();
                    }
                } else {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    keys.add(entry.getKey());
                }
                if (!isOpen) {
                    Announce announce = new Announce();
                    announce.setContent(data);
                    savaHistoryAnnounce(announce, PosHistoryAnnounce, shopId);
                }
            }
            posMap = removeMapVal(posMap, keys);
            PosClients_2_0.put(shopId, posMap);
            log.info("发送给小辉哥-------->"+data);
        } else {
            Announce announce = new Announce();
            announce.setContent(data);
            savaHistoryAnnounce(announce, PosHistoryAnnounce, shopId);
        }
    }


    /**
     * 给指定的  Pos 端 发送 订单信息
     *
     * @param shopId
     * @param order
     * @throws InterruptedException
     * @throws IOException
     */
    public static void sendOrderToPosNoPay(String shopId, Order order) throws InterruptedException {
        JSONObject data = new JSONObject(order);
        data.put("dataType", "nopay");
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    try {
                        entry.getValue().sendMessage(returnMessage);
                        isOpen = true;
                    } catch (Exception e) {
//						暂未做停止处理
//						Thread.sleep(3000);
//						sendOrderToPos(shopId, order);
                        e.printStackTrace();
                    }
                } else {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    keys.add(entry.getKey());
                }
            }
            if (!isOpen) {
                savaHistoryOrder(order, PosHistoryOrder);
            }
            posMap = removeMapVal(posMap, keys);
            PosClients.put(shopId, posMap);
        } else {
            savaHistoryOrder(order, PosHistoryOrder);
        }
    }

    /**
     * 给指定的  Pos 端 发送 订单信息
     *
     * @param shopId
     * @throws IOException
     */
    public static void sendPlatformOrderToPos(String shopId, String id, Integer type) throws IOException {

        JSONObject data = new JSONObject();
        data.put("dataType", "platform");
        data.put("id", id);
        data.put("type", type);
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 第一台 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    entry.getValue().sendMessage(returnMessage);
                    isOpen = true;
                } else {
                    posMap.remove(entry.getKey());
                }
            }

            PosClients.put(shopId, posMap);
        }
    }

    /**
     * 给指定的  电视端  发送 订单信息
     *
     * @param shopId 指定店铺
     * @param order  订单信息
     * @throws IOException
     */
    public static void sendOrderToTv(String shopId, Order order) throws IOException {
        JSONObject data = new JSONObject(order);
        data.put("dataType", "current");
        TextMessage returnMessage = new TextMessage(data.toString());
        Map<String, WebSocketSession> tvMap = TvClients.get(shopId);
        if (tvMap != null && !tvMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : tvMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    entry.getValue().sendMessage(returnMessage);
                    isOpen = true;
                } else {
                    entry.getValue().close();
                    tvMap.remove(entry.getKey());
                }
            }
            if (!isOpen) {
                log.error("店铺ID：" + shopId + "【TvClients不为空】  ------> 没有任何在线的 Tv 端");
                log.error("店铺ID：" + shopId + "【TvClients不为空】  ------> 将订单信息保存起来， Tv端  登录后发送     orderId ： " + order.getId());
                savaHistoryOrder(order, TvHistoryOrder);
            }
            tvMap = removeMapVal(tvMap, keys);
            TvClients.put(shopId, tvMap);
        } else {
            log.error("店铺ID：" + shopId + "【TvClients为空】  ------> 没有任何在线的 Tv 端");
            log.error("店铺ID：" + shopId + "【TvClients为空】  ------> 将订单信息保存起来，登录后发送     orderId ： " + order.getId());
            savaHistoryOrder(order, TvHistoryOrder);
        }
    }

    /**
     * 将 订单信息 保存在 对应的 店铺  历史订单 集合中
     *
     * @param order
     * @param historyMap
     */
    public static void savaHistoryOrder(Order order, Map<String, List<Order>> historyMap) {
        String shopId = order.getShopDetailId();
        List<Order> orders = historyMap.get(shopId);
        if (orders != null) {
            orders.add(order);
            log.info("已有未发送的订单集合   --->  订单保存成功    shopId ：" + shopId + "   orderId：" + order.getId());
        } else {
            orders = new ArrayList<Order>();
            orders.add(order);
            log.info("新建一个未发送的订单集合   --->  订单保存成功    shopId ：" + shopId + "   orderId：" + order.getId());
        }
        historyMap.put(shopId, orders);
        if (orders.size() > 100) {
            orders.clear();
        }
    }


    public static void savaHistoryAnnounce(Announce announce, Map<String, List<Announce>> historyMap, String shopId) {
        List<Announce> announces = historyMap.get(shopId);
        if (announces == null) {
            announces = new ArrayList<>();
            announces.add(announce);
        } else {
            if (!announces.contains(announce)) {
                announces.add(announce);
            }
        }
        historyMap.put(shopId, announces);

    }


    /**
     * 根据 店铺ID 查询  此会话是否 有历史订单没有推送显示
     *
     * @param shopId
     */
    public void checkHistoryOrder(String shopId, String client, WebSocketSession session) {
        try {
            if ("pos".equals(client)) {
                sendHistoryOrder(shopId, PosHistoryOrder, session, client);
                log.info("发送历史订单成功  ----> 清空店铺 ：" + shopId + "   Pos端   历史订单集合");
            }
//            else if ("tv".equals(client)) {
//                sendHistoryOrder(shopId, TvHistoryOrder, session, client);
//                log.info("发送历史订单成功  ----> 清空店铺 ：" + shopId + "   Tv端   历史订单集合");
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送 指定 历史订单中保存的订单信息
     *
     * @param shopId          指定的店铺
     * @param historyOrderMap 指定端的订单信息集合（Pos/Tv）
     * @param session         当前连接的 webSession
     * @throws IOException
     */
    public static void sendHistoryOrder(String shopId, Map<String, List<Order>> historyOrderMap, WebSocketSession session,
                                        String client) throws IOException {
        List<Order> orders = historyOrderMap.get(shopId);

        if (orders != null) {
            JSONObject orderData = new JSONObject();
            orderData.put("orders", orders);
            orderData.put("dataType", "history");
            TextMessage returnMessage = new TextMessage(orderData.toString());
            session.sendMessage(returnMessage);
            historyOrderMap.remove(shopId);
        }
    }

    /**
     * 保存 当前连接的 session
     *
     * @param type
     * @param key
     * @param session
     */
    public void saveClientsSession(String type, String key, WebSocketSession session) {
        switch (type) {
            case "pos":
                saveSession(PosClients, key, session, "pos");
                break;
            case "tv":
                saveSession(TvClients, key, session, "tv");
                break;
            case "queue":
                saveSession(GeekPosClients, key, session, "queue");
                break;
            case "pos2.0":
                saveSession(PosClients_2_0, key, session, "pos2.0");
        }
    }

    /**
     * 将 session 保存在指定  clients Map中 （Pos/Tv）
     *
     * @param clientsMap
     * @param shopId
     * @param session
     */
    public void saveSession(Map<String, Map<String, WebSocketSession>> clientsMap, String shopId, WebSocketSession session, String type) {
        Map<String, WebSocketSession> sessionMap = clientsMap.get(shopId);
        if (sessionMap != null && !sessionMap.isEmpty()) {
            if ("pos".equals(type)) {//如果是 Pos 端，则将之前登录的 客户端 踢下线 update：2017年5月2日 17:47:14
//                commandToPos(shopId, "exit");
//                removeMapAllVal(sessionMap);
            }else if("queue".equals(type)){
//                commandToQueue(shopId, "exit");
//                removeMapAllVal(sessionMap);
            }
            if (!sessionMap.containsKey(session.getId())) {
                sessionMap.put(session.getId(), session);
            }
        } else {
            sessionMap = new ConcurrentHashMap<String, WebSocketSession>();
            sessionMap.put(session.getId(), session);
        }


        clientsMap.put(shopId, sessionMap);
        commandToTv(shopId, "loginSuccess", null);
    }

    /**
     * 将 session 从 clients Map 中移除
     * lmx   2017年11月24日
     * @param clientsMap
     * @param shopId
     * @param session
     */
    public void removeSession(Map<String, Map<String, WebSocketSession>> clientsMap, String shopId, WebSocketSession session){
        if(clientsMap.containsKey(shopId)){
            Map<String, WebSocketSession> sessionMap = clientsMap.get(shopId);
            if (sessionMap != null && !sessionMap.isEmpty()) {
                if(sessionMap.containsKey(session.getId())){
                    sessionMap.remove(session.getId());
                    clientsMap.put(shopId, sessionMap);
                }
            }
        }
    }


    /**
     * 给Pos端发送特定命令
     *
     * @param shopId    指定要执行命令的店铺
     * @param operation 要执行的命令
     */
    public void commandToPos(String shopId, String operation) {
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
            if (entry.getValue().isOpen()) {
                JSONObject data = new JSONObject();
                data.put("dataType", "command");
                data.put("operation", operation);
                TextMessage returnMessage = new TextMessage(data.toString());
                try {
                    entry.getValue().sendMessage(returnMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void commandToQueue(String shopId, String operation) {
        Map<String, WebSocketSession> posMap = GeekPosClients.get(shopId);
        for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
            if (entry.getValue().isOpen()) {
                JSONObject data = new JSONObject();
                data.put("dataType", "command");
                data.put("operation", operation);
                TextMessage returnMessage = new TextMessage(data.toString());
                try {
                    entry.getValue().sendMessage(returnMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 给 店铺 的 指定端 里所有在线的端口 发送消息
     *
     * @param clientsMap    指定端（Pos/Tv）
     * @param shopId        指定店铺
     * @param returnMessage 发送的消息
     * @throws IOException
     */
    public static void sendMsg(Map<String, Map<String, WebSocketSession>> clientsMap, String shopId, TextMessage returnMessage) throws IOException {
        Map<String, WebSocketSession> sessionMap = clientsMap.get(shopId);
        if (sessionMap != null && !sessionMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
                WebSocketSession session = entry.getValue();
                if (session.isOpen()) {
                    session.sendMessage(returnMessage);
                } else {
                    session.close();
                    keys.add(entry.getKey());
                }
            }
            sessionMap = removeMapVal(sessionMap, keys);
            //更新
            clientsMap.put(shopId, sessionMap);
        }
    }

    /**
     * 取消订单后准备中的订单删除
     *
     * @param order
     */
    public static void cancelRedayOrder(Order order, ShopDetail shop, BrandSetting brandSetting) {
        //通知 tv端，移除准备中的订单信息
        JSONObject data = new JSONObject(order);
        data.put("dataType", "cancelReadyOrder");
        TextMessage returnMessage = new TextMessage(data.toString());
        try {
            System.out.println("开始链接tv端移除准备中的订单----------");
            if (shop.getShopMode() == ShopMode.CALL_NUMBER && brandSetting.getCallTvType() == 1) {
                try {
                    sendAndroidUnicast("remove", order.getVerCode(), order.getId(), "", shop);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                data.put("operationType", "cancelOrder");
                log.info("推送了拒绝或者取消订单，在TV准备中消失！");
                sendOrderToTvTest(order.getShopDetailId(), data);
            }
            System.out.println("tv端准备中的订单移除完毕---");
        } catch (Exception e) {
            log.error("543----> 移除 Tv 端 订单信息失败");
            e.printStackTrace();
        }


    }

    /**
     * 移除 当前 Map 中 指定的 keys集合
     *
     * @param currentMap
     * @param keys
     * @return
     */
    private static Map<String, WebSocketSession> removeMapVal(Map<String, WebSocketSession> currentMap, List<String> keys) {
        for (String key : keys) {
            if (currentMap.containsKey(key)) {
                try {
                    if (currentMap.get(key) != null && currentMap.get(key).isOpen()) {
                        currentMap.get(key).close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                currentMap.remove(key);
            }
        }
        return currentMap;
    }

    /**
     * 关闭所有 session 链接
     * 并且清楚 Map 所有的值
     *
     * @param currentMap
     */
    private static void removeMapAllVal(Map<String, WebSocketSession> currentMap) {
        Set<String> keys = currentMap.keySet();
        for (String key : keys) {
            try {
                if (currentMap.get(key) != null && currentMap.get(key).isOpen()) {
                    currentMap.get(key).close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        currentMap.clear();
    }


    /**
     * 友盟消息推送
     *
     * @param type
     * @param code
     * @param orderId
     * @param data
     * @throws Exception
     */
    public static void sendAndroidUnicast(String type, String code, String orderId, String data, ShopDetail shop) throws Exception {
        AndroidUnicast unicast = new AndroidUnicast(shop.getAppkey(), shop.getAppMasterSecret());
        unicast.setDeviceToken(shop.getDeviceToken());
        unicast.setTicker("Android unicast ticker");
        unicast.setTitle("中文的title");
        unicast.setText("Android unicast text");
        unicast.goAppAfterOpen();
        unicast.setDisplayType(AndroidNotification.DisplayType.NOTIFICATION);
        unicast.setProductionMode();
        // Set customized fields
        unicast.setExtraField("type", type);
        unicast.setExtraField("code", code);
        unicast.setExtraField("orderId", orderId);
        unicast.setExtraField("data", data);
        client.send(unicast);
    }

    /**
     * 给指定的  电视端  发送 订单信息
     *
     * @param shopId 指定店铺
     * @param data   订单信息
     * @throws IOException
     */
    public static void sendOrderToTvTest(String shopId, JSONObject data) throws IOException {
        data.put("dataType", "current");
        TextMessage returnMessage = new TextMessage(data.toString());
        Map<String, WebSocketSession> tvMap = TvClients.get(shopId);
        if (tvMap != null && !tvMap.isEmpty()) {
            boolean isOpen = false;
            List<String> keys = new ArrayList<>();
            //只给一个客户端发送订单消息     -- update 给所有在线的Tv发送消息  2017年4月25日 11:51:02
            for (Map.Entry<String, WebSocketSession> entry : tvMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    entry.getValue().sendMessage(returnMessage);
                    isOpen = true;
//                    break;
                } else {
                    keys.add(entry.getKey());
                }
            }
            if (!isOpen) {
                log.error("店铺ID：" + shopId + "  ------> 没有任何在线的 Tv 端");
            }
            //更新当前店铺的Tv客户端
            tvMap = removeMapVal(tvMap, keys);
            TvClients.put(shopId, tvMap);
            log.info("店铺ID：" + shopId + "  ------> 当前店铺在线数：" + tvMap.size());
        } else {
            log.error("店铺ID：" + shopId + "  ------> 没有任何在线的 Tv 端");
        }
    }

    /**
     * 给Tv端发送特定命令
     *
     * @param shopId    指定要执行命令的店铺
     * @param operation 要执行的命令
     * @param parameter 额外参数
     */
    public void commandToTv(String shopId, String operation, Map parameter) {
        Map<String, WebSocketSession> tvMap = TvClients.get(shopId);
        if (tvMap == null) {
            return;
        }
        for (Map.Entry<String, WebSocketSession> entry : tvMap.entrySet()) {
            if (entry.getValue().isOpen()) {
                JSONObject data;
                if (parameter != null) {
                    data = new JSONObject(parameter);
                } else {
                    data = new JSONObject();
                }


                data.put("operationType", operation);
                data.put("dataType", "command");
                TextMessage returnMessage = new TextMessage(data.toString());
                try {
                    entry.getValue().sendMessage(returnMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取 Pos / Tv 当前在线数量
     *
     * @param type
     * @param shopId
     * @return
     */
    public static Map getConnClients(String type, String shopId) {
        Map client = new HashMap();
        switch (type) {
            case "pos":
                client = StringUtils.isEmpty(shopId) ? getAllClients(PosClients) : getClientsByShopId(PosClients, shopId);
                break;
            case "tv":
                client = StringUtils.isEmpty(shopId) ? getAllClients(TvClients) : getClientsByShopId(TvClients, shopId);
                break;
        }
        return client;
    }

    public static Map getClientsByShopId(Map<String, Map<String, WebSocketSession>> clientsMap, String shopId) {
        Map client = new HashMap();
        int count = 0;
        Map<String, WebSocketSession> currentClient = clientsMap.get(shopId);
        if (currentClient != null && !currentClient.isEmpty()) {
            for (Map.Entry<String, WebSocketSession> entry : currentClient.entrySet()) {
                if (entry.getValue().isOpen()) {
                    count++;
                }
            }
        }
        client.put(shopId, count);
        return client;
    }

    public static Map getAllClients(Map<String, Map<String, WebSocketSession>> clientsMap) {
        Map client = new HashMap();
        Iterator<Map.Entry<String, Map<String, WebSocketSession>>> clientEntries = clientsMap.entrySet().iterator();
        while (clientEntries.hasNext()) {
            int count = 0;
            Map.Entry<String, Map<String, WebSocketSession>> currentClient = clientEntries.next();
            for (Map.Entry<String, WebSocketSession> entry : currentClient.getValue().entrySet()) {
                if (entry.getValue().isOpen()) {
                    count++;
                }
            }
            client.put(currentClient.getKey(), count);
        }
        return client;
    }

    /**
     * 给指定的  geekpos端 发送 订单信息
     *
     * @param shopId
     * @throws IOException
     */
    public static void queueOrderMessage(String id, String shopId) throws IOException {

        JSONObject data = new JSONObject();
        data.put("dataType", "getNumber");
        data.put("id", id);
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 第一台 Pos端推送消息
        Map<String, WebSocketSession> posMap = GeekPosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            boolean isOpen = false;
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    entry.getValue().sendMessage(returnMessage);
                    isOpen = true;
                } else {
                    posMap.remove(entry.getKey());
                }
            }

            PosClients.put(shopId, posMap);
        }
    }

    /**
     * 给指定的  Pos 端 发送 差评订单消息
     *
     * @param shopId
     * @throws IOException
     */
    public static void sendBadAppraisePrintOrderToPos(String orderId, String shopId) throws IOException {
        JSONObject data = new JSONObject();
        data.put("id", orderId);
        data.put("dataType", "badAppraisePrintOrder");
        log.info("准备推送差评订单信息：" + data.toString());
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 第一台 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    try {
                        entry.getValue().sendMessage(returnMessage);
                    } catch (Exception e) {
//						暂未做停止处理
                        e.printStackTrace();
                    }
                } else {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    keys.add(entry.getKey());
                }
            }
            posMap = removeMapVal(posMap, keys);
            PosClients.put(shopId, posMap);
        }
    }

    /**
     * 给指定的 Pos端发送发票订单消息
     * @param shopId
     * @throws IOException
     */
    public static void sendReceiptPrintOrder(String shopId, JSONObject data) throws IOException {
        log.info("发票打印信息发送"+data.toString());
        TextMessage returnMessage = new TextMessage(data.toString());
        //给该店铺下在线的 第一台 Pos端推送消息
        Map<String, WebSocketSession> posMap = PosClients.get(shopId);
        if (posMap != null && !posMap.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, WebSocketSession> entry : posMap.entrySet()) {
                if (entry.getValue().isOpen()) {
                    try {
                        entry.getValue().sendMessage(returnMessage);
                    } catch (Exception e) {
//						暂未做停止处理
                        e.printStackTrace();
                    }
                } else {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    keys.add(entry.getKey());
                }
            }
            posMap = removeMapVal(posMap, keys);
            PosClients.put(shopId, posMap);
        }
    }
}

