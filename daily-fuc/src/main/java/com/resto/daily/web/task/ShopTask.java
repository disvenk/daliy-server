//package com.resto.daily.web.task;
//
//import com.resto.brand.core.util.*;
//import com.resto.brand.web.model.*;
//import com.resto.brand.web.service.*;
//import com.resto.daily.web.rpcinterceptors.DataSourceTarget;
//import com.resto.daily.web.util.RedisUtil;
//import com.resto.shop.web.constant.Common;
//import com.resto.shop.web.model.Coupon;
//import com.resto.shop.web.model.Customer;
//import com.resto.shop.web.service.CouponService;
//import com.resto.shop.web.service.CustomerService;
//import org.I0Itec.zkclient.ZkClient;
//import org.I0Itec.zkclient.serialize.SerializableSerializer;
//import org.apache.commons.collections.CollectionUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.time.DateFormatUtils;
//import org.json.JSONObject;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.io.Serializable;
//import java.sql.*;
//import java.util.*;
//import java.util.Date;
//
//import static com.resto.brand.core.util.HttpClient.doPostAnsc;
//
///**
// * Created by KONATA on 2016/7/14.
// */
//@Component("shopTask")
//public class ShopTask {
//
//    @Autowired
//    private DatabaseConfigService databaseConfigService;
//
//    @Autowired
//    private BrandService brandService;
//
//    @Autowired
//    private BrandSettingService brandSettingService;
//
//    @Autowired
//    private ShopDetailService shopDetailService;
//
//    @Autowired
//    private CouponService couponService;
//
//    @Autowired
//    private CustomerService customerService;
//
//    @Autowired
//    private WechatConfigService wechatConfigService;
//
//    private static String url = "http://10.25.23.60/pos/posAction";
//
//
//    @Scheduled(cron = "0 0 10 * * ?")   //每天早上10点
//    public void coupon() throws ClassNotFoundException {
//        List<Brand> brands = brandService.selectList();
////        List<Brand> brands = new ArrayList<>();
////        brands.add(brandService.selectByPrimaryKey("2f83afee7a0e4822a6729145dd53af33"));
//        for (Brand brand : brands) {
//            DataSourceTarget.setDataSourceName(brand.getId());
//            BrandSetting brandSetting = brandSettingService.selectByBrandId(brand.getId());
//            List<ShopDetail> shopDetails = shopDetailService.selectByBrandId(brand.getId());
//            List<Coupon> total = new ArrayList<>();
//            List<Coupon> brandList = couponService.getCouponByShopId(brand.getId(), brandSetting.getRecommendTime(), 0);
//            if (!CollectionUtils.isEmpty(brandList)) {
//                total.addAll(brandList);
//            }
//            for (ShopDetail shopDetail : shopDetails) {
//                List<Coupon> shopList = couponService.getCouponByShopId(shopDetail.getId(), shopDetail.getRecommendTime(), 1);
//                if (!CollectionUtils.isEmpty(shopList)) {
//                    total.addAll(shopList);
//                }
//            }
//            for (Coupon coupon : total) {
//                Customer customer = customerService.selectById(coupon.getCustomerId());
//                if(customer == null){
//                    continue;
//                }
//                WechatConfig config = wechatConfigService.selectByBrandId(brand.getId());
//                ShopDetail shopDetail = shopDetailService.selectByPrimaryKey(coupon.getShopDetailId());
//                StringBuffer str = new StringBuffer();
//                String name = StringUtils.isEmpty(coupon.getShopDetailId()) ? brand.getBrandName() : shopDetail.getName();
//                String url = StringUtils.isEmpty(coupon.getShopDetailId()) ? "?subpage=tangshi" : "?subpage=tangshi&shopId=" + shopDetail.getId();
//                int day = StringUtils.isEmpty(coupon.getShopDetailId()) ? brandSetting.getRecommendTime() : shopDetail.getRecommendTime();
//                String jumpurl = brandSetting.getWechatWelcomeUrl() + url + "";
//                str.append("优惠券到期提醒\n");
//                str.append("" + name + "温馨提醒您：您价值" + coupon.getValue() + "元的\"" + coupon.getName() + "\"" + day + "天后即将到期，<a href='" + jumpurl + "'>快来尝尝我们的新菜吧~</a>");
//                String result = WeChatUtils.sendCustomerMsg(str.toString(), customer.getWechatId(), config.getAppid(), config.getAppsecret());//提交推送
//                Map map = new HashMap(4);
//                map.put("brandName", brand.getBrandName());
//                map.put("fileName", customer.getId());
//                map.put("type", "UserAction");
//                map.put("content", "系统向用户:" + customer.getNickname() + "推送微信消息:" + str.toString() + ",请求服务器地址为:" + MQSetting.getLocalIP());
//                doPostAnsc(LogUtils.url, map);
//                if (brandSetting.getIsSendCouponMsg() == Common.YES) {
//                    Map param = new HashMap();
//                    param.put("shop", name);
//                    param.put("price", coupon.getValue() + "");
//                    param.put("name", name);
//                    param.put("day", day + "");
//                    SMSUtils.sendMessage(customer.getTelephone(), new JSONObject(param).toString(), "餐加", "SMS_43790004", null);
//                }
//            }
//
//        }
//    }
//
//    @Scheduled(cron = "0/30 * * * * ?")   //每5秒执行一次
//    public void job2() throws InterruptedException {
//        String sign = "";
//        Boolean error = false;
//        Brand currentBrand = null;
//        Map param = new HashMap();
//        try {
//            List<Brand> brandList = brandService.selectList();
//            for(Brand brand : brandList){
//                error = brand.getUseState() == 1 ;
//                currentBrand = brand;
//                sign = brand.getBrandSign();
//                if(sign.equals("test") || sign.equals("ecosystem")){
//                    continue;
//                }
//                param.put("brandSign", sign);
//                DataSourceTarget.setDataSourceName(brand.getId());
//                Customer customer =  customerService.getCustomerLimitOne();
//                BrandSetting brandSetting = brandSettingService.selectByBrandId(brand.getId());
//                String title = brandSetting.getOpenHttps() == 1 ? "https" : "http";
//                HttpRequest request =
//                        HttpRequest.get(title+"://"+sign+".restoplus.cn/wechat/index?qiehuan=qiehuan&web=open&userId="+customer.getId());
//                int time = 0;
//                while(StringUtils.isEmpty(request.body())){
//                    if(time > 3){
//                        break;
//                    }
//                    request = HttpRequest.get(title+"://"+sign+".restoplus.cn/wechat/index?qiehuan=qiehuan&web=open&userId="+customer.getId());
//                    time++;
//                }
//
//
//                Boolean result = StringUtils.isEmpty(request.body());
//                if (result && !error) {
//                    System.out.println(request.badRequest());
//                    brand.setUseState(1);
//                    brandService.update(brand);
//                    Map map = new HashMap(4);
//                    map.put("brandName", sign);
//                    map.put("fileName","error" );
//                    map.put("type", "errorLog");
//                    map.put("content",sign + "访问异常");
//                    doPostAnsc(url, map);
//                    SMSUtils.sendMessage("18621943805",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("18616997698",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("15000313810",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("17671111590",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("18796899883",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                }else if (!result && error){
//                    brand.setUseState(0);
//                    brandService.update(brand);
//                    Map map = new HashMap(4);
//                    map.put("brandName", sign);
//                    map.put("fileName","error" );
//                    map.put("type", "errorLog");
//                    map.put("content",sign + "恢复正常，请放心使用");
//                    doPostAnsc(url, map);
//                    SMSUtils.sendMessage("18621943805",  new JSONObject(param).toString(), "餐加", "SMS_70120770", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("18616997698",  new JSONObject(param).toString(), "餐加", "SMS_70120770", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("15000313810",  new JSONObject(param).toString(), "餐加", "SMS_70120770", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("17671111590",  new JSONObject(param).toString(), "餐加", "SMS_70120770", null);
//                    Thread.sleep(2000);
//                    SMSUtils.sendMessage("18796899883",  new JSONObject(param).toString(), "餐加", "SMS_70120770", null);
//                }
//            }
//        }catch (Exception e){
//            if(!error){
//                currentBrand.setUseState(1);
//                brandService.update(currentBrand);
//                Map map = new HashMap(4);
//                map.put("brandName", sign);
//                map.put("fileName","error" );
//                map.put("type", "errorLog");
//                map.put("content",sign + "访问异常");
//                doPostAnsc(url, map);
//                SMSUtils.sendMessage("18621943805",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("18616997698",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("15000313810",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("17671111590",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("18796899883",  new JSONObject(param).toString(), "餐加", "SMS_70095632", null);
//            }
//
//        }
//
//    }
//
//
//    @Scheduled(cron = "0/30 * * * * ?")   //每5秒执行一次
//    public void job() throws InterruptedException {
//        HttpRequest request =
//                HttpRequest.get("http://pos.eco.restoplus.cn/pos");
//        try {
//            Boolean result = StringUtils.isEmpty(request.body());
//            if (result) {
//                SMSUtils.sendMessage("18621943805", null, "餐加", "SMS_67110736", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("18616997698", null, "餐加", "SMS_67110736", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("15000313810", null, "餐加", "SMS_67110736", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("17671111590", null, "餐加", "SMS_67110736", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("18796899883", null, "餐加", "SMS_67110736", null);
//            }
//        } catch (Exception e) {
//            SMSUtils.sendMessage("18621943805", null, "餐加", "SMS_67110736", null);
//            Thread.sleep(2000);
//            SMSUtils.sendMessage("18616997698", null, "餐加", "SMS_67110736", null);
//            Thread.sleep(2000);
//            SMSUtils.sendMessage("15000313810", null, "餐加", "SMS_67110736", null);
//            Thread.sleep(2000);
//            SMSUtils.sendMessage("17671111590", null, "餐加", "SMS_67110736", null);
//            Thread.sleep(2000);
//            SMSUtils.sendMessage("18796899883", null, "餐加", "SMS_67110736", null);
//        }
//    }
//
//
//    public static void main(String[] args) {
//        ZkClient zkClient = new ZkClient("106.14.96.154:2181", 10000, 10000, new SerializableSerializer());
//        List<String> nodes = zkClient.getChildren("/registry");
//        for (String node : nodes) {
//            List<String> child = zkClient.getChildren("/registry/" + node);
//            System.out.println(node + " ----" + child);
//        }
//    }
//
//
//    @Scheduled(cron = "0 0 6 * * ?")   //每天早上5点
//    public void job1() throws ClassNotFoundException {
//        List<DatabaseConfig> databaseConfigs = databaseConfigService.selectList();
//        String url = null;
//        String driver = null;
//        String username = null;
//        String password = null;
//        Connection con = null;
//        Statement st = null;
//        ResultSet rs = null;
//        MemcachedUtils.flus();
//        int row = 0;
//        for (DatabaseConfig databaseConfig : databaseConfigs) {
//            try {
//                //查询 选中的数据库信息
//                DatabaseConfig config = databaseConfigService.selectByPrimaryKey(databaseConfig.getId());
//
//                url = config.getUrl();
//                driver = config.getDriverClassName();
//                Class.forName(driver);
//                username = config.getUsername();
//                password = config.getPassword();
//                con = DriverManager.getConnection(url, username, password);
//                st = con.createStatement();// 创建sql执行对象
//
//
//                String time = DateFormatUtils.format(new Date(), "yyyy-MM-dd");
//                String getTime = "select * from tb_free_day " +
//                        "  where free_day=" + "'" + time + "'";
//                rs = st.executeQuery(getTime);
//                Boolean isWorkDay = true;
//                if (rs.next()) {
//                    isWorkDay = false;
//                }
//
//                String day = isWorkDay == true ? "stock_working_day" : "stock_weekend";
//
//                String currentSql = "update tb_article set is_empty = 0 ,empty_remark = NULL , current_working_stock " +
//                        "= " + day;
//
//
//                if (currentSql.startsWith("select")) {
//                    rs = st.executeQuery(currentSql);
//                } else {
//                    row += st.executeUpdate(currentSql);
//                }
//
//                //初始化有规格餐品库存
//                String sql = "update tb_article_price set empty_remark = NULL , current_working_stock = " + day;
//                st.executeUpdate(sql);
//
//                //初始化套餐库存
//                String initSuit = "update tb_article t , " +
//                        "( " +
//                        "select min(count) as count,article_name,id from (select max(t4.current_working_stock) as count ,t2.name,t.name as article_name,t.id from tb_article t " +
//                        "INNER JOIN tb_meal_attr t2 on t2.article_id = t.id " +
//                        "INNER JOIN tb_meal_item t3 on t3.meal_attr_id = t2.id " +
//                        "INNER JOIN tb_article t4 on t4.id = t3.article_id " +
//                        "where t.article_type = 2 " +
//                        "" +
//                        "GROUP BY t2.name " +
//                        ") as r " +
//                        "GROUP BY article_name " +
//                        ") as b " +
//                        "set t.current_working_stock = b.count " +
//                        "where t.id = b.id ";
//                st.executeUpdate(initSuit);
//                //初始化有规格餐品主品库存(等于其子品库存之和)
//                String initSize = " update tb_article t ,( " +
//                        "    select article_id, sum(current_working_stock) as current_working_stock_count " +
//                        "    from tb_article_price " +
//                        "    GROUP BY article_id " +
//                        "    ) as t2 " +
//                        "    set t.current_working_stock = t2.current_working_stock_count " +
//                        "    where t.id = t2.article_id ";
//                st.executeUpdate(initSize);
//            } catch (SQLException e) {
//                e.printStackTrace();
//            } finally {
//                close(con, st, rs);
//            }
//        }
//    }
//
//
//    // 关闭相关的对象
//    public void close(Connection con, Statement st, ResultSet rs) {
//        try {
//            if (rs != null) {
//                rs.close();
//            }
//            if (st != null) {
//                st.close();
//            }
//            if (con != null) {
//                con.close();
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }
//
//
//    @Scheduled(cron = "0 0/5 *  * * ? ")
//    public void jiankong() throws InterruptedException, SQLException {
//
//        JdbcUtils jdbcUtils = new JdbcUtils("resto", "restoplus", "com.mysql.jdbc.Driver",
//                "jdbc:mysql://rds64fw2qrd8q0eg95nmo.mysql.rds.aliyuncs.com:3306/resto_brand?useUnicode=true&characterEncoding=utf8");
//        jdbcUtils.getConnection();
//        List<Map<String, Object>> services = new ArrayList<>();
//        List<Map<String, Object>> results = jdbcUtils.findModeResult("" +
//                "select `desc`,id,status,zk_address from zk_config where is_used = 0", null);
//        List<String> servicesAddress = new ArrayList<>();
//        Map<String, Object> map = new HashMap<>();
//        List<String> runningService = new ArrayList<>();
//        if (CollectionUtils.isEmpty(results)) {
//            //没有要监听的zk
//            return;
//        }
//        //得到所有要监听的zk
//
//        for (Map<String, Object> result : results) {
//            Boolean check = true;
//            Integer status = (Boolean) result.get("status") ? 1 : 0;
//            String desc = (String) result.get("desc");
//            String zk_address = (String) result.get("zk_address");
//            Long zk_id = (Long) result.get("id");
//            services = jdbcUtils.findModeResult("" +
//                    "select ali_id,service_address from zk_service_config where is_used = 0 and zk_id = " + zk_id, null);
//            //得到该zk下应该有 服务集合
//            for (Map<String, Object> service : services) {
//                servicesAddress.add(String.valueOf(service.get("service_address")));
//                map.put(String.valueOf(service.get("service_address")), String.valueOf(service.get("ali_id")));
//            }
//            //zk集群的地址
//            if (CollectionUtils.isEmpty(services)) {
//                //该zk不监听服务
//                break;
//            }
//
//            ZkClient zkClient = new ZkClient(zk_address, 10000, 10000, new SerializableSerializer());
//            List<String> nodes = zkClient.getChildren("/registry");
//            for (String node : nodes) {
//                if (!check) {
//                    break;
//                }
//                List<String> childs = zkClient.getChildren("/registry/" + node);
//                if (CollectionUtils.isEmpty(childs)) {
//                    continue;
//                } else {
//                    for (String c : childs) {
//                        runningService.add(c.substring(0, c.indexOf("-")));
//                    }
//                    if (childs.size() != servicesAddress.size()) {
//                        //运行中的服务 和 应该有的服务数量不一致
//                        for (String address : servicesAddress) {
//
//                            if (!runningService.contains(address)) {
//                                check = false;
//                                if (status == 1) {
//                                    break;
//                                }
//                                Map param = new HashMap();
//                                param.put("address", map.get(address));
//                                SMSUtils.sendMessage("18621943805", new JSONObject(param).toString(), "餐加", "SMS_67015002", null);
//                                Thread.sleep(2000);
//                                SMSUtils.sendMessage("18616997698", new JSONObject(param).toString(), "餐加", "SMS_67015002", null);
//                                Thread.sleep(2000);
//                                SMSUtils.sendMessage("15000313810", new JSONObject(param).toString(), "餐加", "SMS_67015002", null);
//                                Thread.sleep(2000);
//                                SMSUtils.sendMessage("17671111590", new JSONObject(param).toString(), "餐加", "SMS_67015002", null);
//                                Thread.sleep(2000);
//                                SMSUtils.sendMessage("18796899883", new JSONObject(param).toString(), "餐加", "SMS_67015002", null);
//                                String sql = "update zk_config set status = 1 where id = " + zk_id;
//                                jdbcUtils.updateByPreparedStatement(sql, null);
//                            }
//                        }
//                    }
//
//                }
//            }
//
//            if (check && status == 1) {
//                String sql = "update zk_config set status = 0 where id = " + zk_id;
//                jdbcUtils.updateByPreparedStatement(sql, null);
//                Map param = new HashMap();
//                param.put("address", desc);
//                SMSUtils.sendMessage("18621943805", new JSONObject(param).toString(), "餐加", "SMS_67300749", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("18616997698", new JSONObject(param).toString(), "餐加", "SMS_67300749", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("15000313810", new JSONObject(param).toString(), "餐加", "SMS_67300749", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("17671111590", new JSONObject(param).toString(), "餐加", "SMS_67300749", null);
//                Thread.sleep(2000);
//                SMSUtils.sendMessage("18796899883", new JSONObject(param).toString(), "餐加", "SMS_67300749", null);
//            }
//
//        }
//    }
//
//
//
//    @Scheduled(cron = "0 0 6 * * ?")   //每天早上5点
////    @Scheduled(cron = "0/30 * * * * ?")   //每5秒执行一次
//    public void clearRedis() throws ClassNotFoundException {
//        RedisUtil.removePattern("*");
//    }
//}
