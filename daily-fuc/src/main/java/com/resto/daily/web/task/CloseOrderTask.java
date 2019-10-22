//package com.resto.daily.web.task;
//
//import com.resto.brand.core.util.Encrypter;
//import com.resto.brand.core.util.JdbcUtils;
//import com.resto.brand.web.model.Brand;
//import com.resto.brand.web.model.ShopDetail;
//import com.resto.brand.web.service.BrandService;
//import com.resto.brand.web.service.ShopDetailService;
//import com.resto.shop.web.service.OrderService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.sql.SQLException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.List;
//import java.util.Map;
//
///*
//* @Author CARL
//* @Date 2017/5/11 11:30
//* @Description
//*/
//@Component("closeOrderTask")
//public class CloseOrderTask {
//
//    @Autowired
//    private BrandService brandService;
//    @Autowired
//    private OrderService orderService;
//
//    private static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
//
//    @Scheduled(cron = "0 0 0/2 * * ? ")    //每2小时执行一次
//    public void job1() throws ClassNotFoundException {
//        List<Brand> brands = brandService.selectList();
//
//        String url = null;
//        String driver = null;
//        String username = null;
//        String password = null;
//        for (Brand brand : brands) {
//            if("31946c940e194311b117e3fff5327215".equals(brand.getId())){
//                try {
//                    //查询 选中的数据库信息
//                    Brand b = brandService.selectById(brand.getId());
//                    url = b.getDatabaseConfig().getUrl();
//                    driver = b.getDatabaseConfig().getDriverClassName();
//                    Class.forName(driver);
//                    username = Encrypter.decrypt(b.getDatabaseConfig().getUsername());
//                    password = Encrypter.decrypt(b.getDatabaseConfig().getPassword());
//                    JdbcUtils jdbcUtils = new JdbcUtils(username, password, driver, url);
//                    jdbcUtils.getConnection();
//
//                    String sql = "select id from tb_order where (accounting_time = '" + getNowDay() + "' or accounting_time = '" + getYesterday() + "') and order_state = 1 and 43200 < (now() - create_time) and pay_type = 0 and pay_mode in (1,2)";
//                    List<Map<String, Object>> list = jdbcUtils.findModeResult(sql,null);
//
//                    for(int i = 0; i < list.size(); i++){
//                        Map<String, Object> map = (Map) list.get(i);
//                        String id = map.get("id").toString();
//                       // orderService.colseOrder(id);
//                    }
//                } catch (SQLException e) {
//                    e.printStackTrace();
//                } finally {
//                    JdbcUtils.close();
//                }
//            }
//        }
//    }
//
//    public static String getNowDay(){
//        Date now = new Date();
//        String dateString = formatter.format(now);
//        return dateString;
//    }
//
//    public static String getYesterday(){
//        Date now = new Date();
//        Date yesterday = new Date(now.getTime() - 86400000);
//        String dateString = formatter.format(yesterday);
//        return  dateString;
//    }
//}
