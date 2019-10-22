//package com.resto.daily.web.task;
//
//import com.resto.brand.core.util.ApplicationUtils;
//import com.resto.brand.core.util.Encrypter;
//import com.resto.brand.core.util.JdbcUtils;
//import com.resto.brand.core.util.WeChatUtils;
//import com.resto.brand.web.model.Brand;
//import com.resto.brand.web.model.ElemeToken;
//import com.resto.brand.web.service.BrandService;
//import com.resto.brand.web.service.ElemeTokenService;
//import eleme.openapi.sdk.config.Config;
//import eleme.openapi.sdk.oauth.OAuthClient;
//import eleme.openapi.sdk.oauth.response.Token;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Resource;
//import java.sql.Connection;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.sql.Statement;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Created by carl on 2017/6/1.
// */
//@Component("elemeTokenTask")
//public class ElemeTokenTask {
//
//    // 设置是否沙箱环境
//    private static final boolean isSandbox = false;
//    // 设置APPKEY
//    private static final String key = "kVB8WfIMAp";
//    // 设置APPSECRET
//    private static final String secret = "03014c7cb99aebd09a94639f08225e13";
//    // 初始化OAuthClient
//    private static OAuthClient client = null;
//    private static Map<String, String> tokenMap = new HashMap<String, String>();
//    private static Config config = null;
//
//    @Autowired
//    private ElemeTokenService elemeTokenService;
//
//    static {
//        // 初始化全局配置工具
//        config = new Config(isSandbox, key, secret);
//        client = new OAuthClient(config);
//    }
//
//    @Scheduled(cron = "0 20 17 * * ?")   //每天7点
//    public void job1() throws ClassNotFoundException {
//        List<ElemeToken> elemeTokens = elemeTokenService.selectList();
//        for (ElemeToken tokenOld : elemeTokens) {
//            Token token = client.getTokenByRefreshToken(tokenOld.getRefreshToken());
//            if (token.isSuccess()) {
//                tokenOld.setAccessToken(token.getAccessToken());
//                tokenOld.setRefreshToken(token.getRefreshToken());
//                tokenOld.setExpiresIn(token.getExpires());
//                tokenOld.setTokenType(token.getTokenType());
//                tokenOld.setUpdateTime(new Date());
//                elemeTokenService.updateSelectByShopId(tokenOld);
//            }
//        }
//    }
//}
