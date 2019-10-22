//package com.resto.daily.web.task;
//
//import com.resto.brand.core.util.MQSetting;
//import com.resto.brand.web.model.Brand;
//import com.resto.brand.web.model.BrandSetting;
//import com.resto.brand.web.model.WechatConfig;
//import com.resto.brand.web.service.BrandService;
//import com.resto.brand.web.service.BrandSettingService;
//import com.resto.brand.web.service.WechatConfigService;
//import com.resto.daily.web.rpcinterceptors.DataSourceTarget;
//import com.resto.shop.web.constant.Common;
//import com.resto.shop.web.model.Customer;
//import com.resto.shop.web.model.NewCustomCoupon;
//import com.resto.shop.web.service.CustomerService;
//import com.resto.shop.web.service.NewCustomCouponService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.List;
//
//@Component("couponTask")
//public class CouponTask {
//
//    @Autowired
//    BrandService brandService;
//
//    @Autowired
//    NewCustomCouponService newCustomCouponService;
//
//    @Autowired
//    CustomerService customerService;
//
//    @Autowired
//    WechatConfigService wechatConfigService;
//
//    @Autowired
//    BrandSettingService brandSettingService;
//
//    //        @Scheduled(cron = "0/10 * *  * * ? ")   //每10秒执行一次
//    @Scheduled(cron = "0 0 7 * * ?")   //每天7点
//    public void addCoupon(){
//        try{
//            //得到所有品牌
//            List<Brand> brands = brandService.selectList();
//            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
//            for (Brand brand : brands) {
//                WechatConfig config = wechatConfigService.selectByBrandId(brand.getId());
//                BrandSetting setting = brandSettingService.selectByBrandId(brand.getId());
//                DataSourceTarget.setDataSourceName(brand.getId());
//                //查询各个品牌下是否存在生日优惠卷
//                List<NewCustomCoupon> couponList = newCustomCouponService.selectBirthCoupon();
//                if (couponList == null) {
//                    continue;
//                } else if (couponList.size() == 0) {
//                    continue;
//                }
//                //得到各个品牌下录入生日信息了的用户
//                List<Customer> customerList = customerService.selectBirthUser();
//                if (customerList == null) {
//                    continue;
//                } else if (customerList.size() == 0) {
//                    continue;
//                }
//                //满足优惠券发放条件后向用户发放优惠卷
//                for (NewCustomCoupon coupon : couponList) {
//                    for (Customer customer : customerList) {
//                        if (customer.getIsBindPhone() == true) {
//                            boolean flg = true;
//                            if (customer.getBirthdayCouponIds() != null) {
//                                String[] birthdayCouponIds = customer.getBirthdayCouponIds().split(",");
//                                for (String birthdayCouponId : birthdayCouponIds) {
//                                    String[] couponIds = birthdayCouponId.split(":");
//                                    String couponId = couponIds[0];
//                                    String year = couponIds[1];
//                                    if (String.valueOf(coupon.getId()).equalsIgnoreCase(couponId) && year.equalsIgnoreCase(yearFormat.format(new Date()))) {
//                                        flg = false;
//                                        break;
//                                    }
//                                }
//                            }
//                            if (!flg) {
//                                continue;
//                            }
//                            //得到用户生日
//                            SimpleDateFormat format = new SimpleDateFormat("MM-dd");
//                            String birthDay = format.format(customer.getCustomerDetail().getBirthDate());
//                            String distanceBirthdayDay = coupon.getDistanceBirthdayDay().toString();
//                            String[] days = getDay(birthDay, distanceBirthdayDay);
//                            if (days == null) {
//                                continue;
//                            }
//                            flg = false;
//                            for (String day : days){
//                                if (day.equalsIgnoreCase(format.format(new Date()))) {
//                                    flg = true;
//                                    break;
//                                }
//                            }
//                            if (flg) {
//                                newCustomCouponService.insertBirthCoupon(coupon, customer, brand, config, setting);
//                            }
//                        }
//
//                    }
//                }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * 得到一个时间前移几天的时间,birthDay为时间,day为前移或后延的天数
//     */
//    public String[] getDay(String birthDay, String day) {
//        try{
//            String[] days = new String[Integer.valueOf(day) + 1];
//            SimpleDateFormat format = new SimpleDateFormat("MM-dd");
//            Date date = format.parse(birthDay);
//            days[0] = format.format(date);
//            for (int i = 1; i <= Integer.valueOf(day); i++){
//                long time = (date.getTime() / 1000) - 1 * 24 * 60 * 60;
//                date.setTime(time * 1000);
//                days[i] = format.format(date);
//            }
//            return days;
//        }catch(Exception e){
//            return null;
//        }
//    }
//}
