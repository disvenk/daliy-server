//package com.resto.daily.web.task;
//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.JSONObject;
//import com.resto.brand.core.util.DateUtil;
//import com.resto.brand.core.util.HttpUtils;
//import com.resto.brand.web.model.ShopDetail;
//import com.resto.brand.web.model.Wether;
//import com.resto.brand.web.service.ShopDetailService;
//import com.resto.brand.web.service.WetherService;
//import org.apache.http.HttpResponse;
//import org.apache.http.util.EntityUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.io.UnsupportedEncodingException;
//import java.util.*;
//
///**
// * Created by yz on 2017-01-17.
// */
//
///**
// * 定时任务 阿里api
// */
//@Component("wetherTask")
//public class WetherTask {
//    @Autowired
//    ShopDetailService shopDetailService;
//
//    @Autowired
//    WetherService wetherService;
//
//
//    final static String AppCode = "bef95664f60a426bad1410d2910f1319";
//
//   @Scheduled(cron = "00 01 00 * * ?")   //每天12点执行
//    // @Scheduled(cron = "0/5 * *  * * ?")
//    public void wetherData() {
//        String host = "https://ali-weather.showapi.com";
//        String path = "/gps-to-weather";
//        String method = "GET";
//        Map<String, String> headers = new HashMap<String, String>();
//        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
//        headers.put("Authorization", "APPCODE " + AppCode);
//        //查询所有的店铺
//        List<ShopDetail> shopDetailList = shopDetailService.selectList();
//        for(ShopDetail s:shopDetailList){
//            Map<String, String> querys = new HashMap<String, String>();
//            querys.put("from", "5");
//            querys.put("lat", s.getLatitude());//纬度
//            querys.put("lng",s.getLongitude());//经度
//            querys.put("need3HourForcast", "0");//是否需要当天每3/6小时一次的天气预报列表。1为需要，0为不
//            querys.put("needAlarm", "0");//是否需要天气预警。1为需要，0为不需要。
//            querys.put("needHourData", "0");//是否需要每小时数据的累积数组。由于本系统是半小时刷一次实时状态，因此实时数组最大长度为48。每天0点长度初始化为0. 1为需要 0为不
//            querys.put("needIndex", "0");//是否需要返回指数数据，比如穿衣指数、紫外线指数等。1为返回，0为不返回。
//            querys.put("needMoreDay", "0");//是否需要返回7天数据中的后4天。1为返回，0为不返回。
//            try {
//                /**
//                 * 重要提示如下:
//                 * HttpUtils请从
//                 * https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/src/main/java/com/aliyun/api/gateway/demo/util/HttpUtils.java
//                 * 下载
//                 *
//                 * 相应的依赖请参照
//                 * https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/pom.xml
//                 */
//                HttpResponse response = HttpUtils.doGet(host, path, method, headers, querys);
//                //获取response的body
//                String result = EntityUtils.toString(response.getEntity());
//                if (result != null) {
//                    JSONObject obj = JSONObject.parseObject(result);
//                    /*获取返回状态码*/
//                    result = obj.getString("showapi_res_code");
//                      /*如果状态码是0说明返回数据成功*/
//                    if (result != null && result.equals("0")) {
//                        String showapi_res_body = obj.getString("showapi_res_body");
//                        JSONObject bodyJson = JSON.parseObject(showapi_res_body);
//                        JSONObject cityInfoJson = JSON.parseObject(bodyJson.getString("cityInfo"));//城市信息
//                        JSONObject f1Json = JSON.parseObject(bodyJson.getString("f1"));//今天天气情况
//                        Wether wether = new Wether();
//                        wether.setAreaId(cityInfoJson.getLong("c1"));//区域id
//                        wether.setDayWeather(f1Json.getString("day_weather"));//白天天气
//                        wether.setNightWeather(f1Json.getString("night_weather"));//晚上天气
//                        wether.setDateTime(DateUtil.getDateString(f1Json.getString("day")));//日期
//                        wether.setWeekady(f1Json.getInteger("weekday"));//星期几
//                        wether.setDayTemperature(f1Json.getInteger("day_air_temperature"));
//                        wether.setNightTemperature(f1Json.getInteger("night_air_temperature"));
//                        wether.setShopId(s.getId());
//                        wether.setCityName(cityInfoJson.getString("c3"));
//                        wether.setProvinceName(cityInfoJson.getString("c7"));
//                        wether.setCode(cityInfoJson.getString("c12"));
//                        wether.setLongitude(s.getLatitude());
//                        wether.setLatitude(s.getLongitude());
//                        wether.setDayWeatherPic(f1Json.getString("day_weather_pic"));
//                        wether.setNightWeatherPic(f1Json.getString("night_weather_pic"));
//                        wetherService.insert(wether);
//                    }
//                }
//            } catch (Exception e) {
//
//            }
//        }
//
//
//    }
//
//
//        public static void main(String[] args) throws UnsupportedEncodingException {
//        String host = "https://ali-weather.showapi.com";
//        String path = "/hour24";
//        String method = "GET";
//        String appcode = "bef95664f60a426bad1410d2910f1319";
//        Map<String, String> headers = new HashMap<String, String>();
//        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
//        headers.put("Authorization", "APPCODE " + appcode);
//        Map<String, String> querys = new HashMap<String, String>();
//        querys.put("area", "海口");
//
//        try {
//            /**
//             * 重要提示如下:
//             * HttpUtils请从
//             * https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/src/main/java/com/aliyun/api/gateway/demo/util/HttpUtils.java
//             * 下载
//             *
//             * 相应的依赖请参照
//             * https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/pom.xml
//             */
//            HttpResponse response = HttpUtils.doGet(host, path, method, headers, querys);
//            System.out.println(response.toString());
//            //获取response的body
//            System.out.println(EntityUtils.toString(response.getEntity()));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//
//}
//
//
//
//
//
